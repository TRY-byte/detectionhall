package com.onvifguard.app.onvif

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.regex.Pattern

data class DiscoveredCamera(
    val ip: String,
    val port: Int,
    val xAddr: String,
    val name: String = "ONVIF Camera"
)

object OnvifDiscovery {

    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val WS_DISCOVERY_PORT = 3702

    suspend fun discoverCameras(
        context: Context,
        timeoutMs: Int = 3000
    ): List<DiscoveredCamera> = withContext(Dispatchers.IO) {
        val found = mutableListOf<DiscoveredCamera>()
        val seenIps = mutableSetOf<String>()

        // Acquire Wifi Multicast lock if possible
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifi?.createMulticastLock("onvif_discovery_lock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 1000

            val messageId = "urn:uuid:" + UUID.randomUUID().toString()
            val probeXml = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
            xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing"
            xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
            xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
    <s:Header>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>
        <wsa:MessageID>$messageId</wsa:MessageID>
        <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
    </s:Header>
    <s:Body>
        <d:Probe>
            <d:Types>dn:NetworkVideoTransmitter</d:Types>
        </d:Probe>
    </s:Body>
</s:Envelope>""".trimIndent()

            val bytes = probeXml.toByteArray(Charsets.UTF_8)
            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            val packet = DatagramPacket(bytes, bytes.size, group, WS_DISCOVERY_PORT)

            // Send probe broadcast
            socket.send(packet)

            val receiveData = ByteArray(4096)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    socket.receive(receivePacket)

                    val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                    val senderIp = receivePacket.address.hostAddress ?: ""

                    // Extract XAddrs
                    val xAddrsPattern = Pattern.compile("(?i)<[^:]*:?XAddrs>([^<]+)</[^:]*:?XAddrs>")
                    val matcher = xAddrsPattern.matcher(response)

                    if (matcher.find()) {
                        val rawAddrs = matcher.group(1)?.trim() ?: ""
                        // Often multiple space-separated URLs
                        val urls = rawAddrs.split("\\s+".toRegex())
                        for (url in urls) {
                            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                                val uriMatcher = Pattern.compile("https?://([^:/]+)(?::(\\d+))?(/.*)?").matcher(url)
                                if (uriMatcher.find()) {
                                    val ip = uriMatcher.group(1) ?: senderIp
                                    val port = uriMatcher.group(2)?.toIntOrNull() ?: 80

                                    if (!seenIps.contains(ip)) {
                                        seenIps.add(ip)
                                        found.add(
                                            DiscoveredCamera(
                                                ip = ip,
                                                port = port,
                                                xAddr = url,
                                                name = "ONVIF Camera ($ip)"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else if (senderIp.isNotBlank() && !seenIps.contains(senderIp)) {
                        seenIps.add(senderIp)
                        found.add(
                            DiscoveredCamera(
                                ip = senderIp,
                                port = 80,
                                xAddr = "http://$senderIp:80/onvif/device_service",
                                name = "ONVIF Device ($senderIp)"
                            )
                        )
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout hit, continue listening until overall timeoutMs
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        found
    }
}
