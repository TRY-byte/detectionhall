package com.onvifguard.app.onvif

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

data class RtspProbeResult(
    val isOpen: Boolean,
    val port: Int,
    val responseCode: Int = 0,
    val responseStatus: String = "",
    val authType: String? = null,
    val rawProtocol: String = "", // RTSP vs HTTP
    val explanation: String = "",
    val error: String? = null
)

object RtspProbe {

    val COMMON_RTSP_PORTS = listOf(554, 8000, 8554, 5544, 80, 10554)

    suspend fun probePort(host: String, port: Int, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun findOpenRtspPort(host: String, preferredPort: Int = 554): Int? = withContext(Dispatchers.IO) {
        if (probePort(host, preferredPort, 1500)) {
            return@withContext preferredPort
        }
        for (p in COMMON_RTSP_PORTS) {
            if (p != preferredPort && probePort(host, p, 1000)) {
                return@withContext p
            }
        }
        null
    }

    suspend fun testRtspOptions(
        host: String,
        port: Int,
        path: String = "/onvif1",
        timeoutMs: Int = 2500
    ): RtspProbeResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val cleanPath = if (path.startsWith("/")) path else "/$path"
            val request = "OPTIONS rtsp://$host:$port$cleanPath RTSP/1.0\r\n" +
                    "CSeq: 1\r\n" +
                    "User-Agent: OnvifGuard\r\n" +
                    "\r\n"

            val out: OutputStream = socket.getOutputStream()
            out.write(request.toByteArray(Charsets.UTF_8))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val statusLine = reader.readLine() ?: ""

            val regex = Regex("""(RTSP|HTTP)/\d\.\d\s+(\d+)\s*(.*)""")
            val match = regex.find(statusLine)
            val proto = match?.groups?.get(1)?.value ?: ""
            val code = match?.groups?.get(2)?.value?.toIntOrNull() ?: 0
            val status = match?.groups?.get(3)?.value ?: statusLine

            var authType: String? = null
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
                    authType = when {
                        line.contains("Digest", ignoreCase = true) -> "Digest"
                        line.contains("Basic", ignoreCase = true) -> "Basic"
                        else -> line.substringAfter(":").trim()
                    }
                }
                line = reader.readLine()
            }

            val explanation = when {
                proto.equals("HTTP", ignoreCase = true) ->
                    "Port $port is an HTTP/SOAP management port (responded with $statusLine). RTSP video usually requires port 554."
                code == 200 ->
                    "Port $port is an active RTSP server! (200 OK - No auth required)"
                code == 401 ->
                    "Port $port is an active RTSP server! (401 Unauthorized - ${authType ?: "Auth"} required)"
                code == 404 ->
                    "Port $port is active RTSP server, but path '$cleanPath' was not found (404). Try another path."
                code in 400..499 ->
                    "Port $port responded with $statusLine."
                else ->
                    "Port $port responded: $statusLine"
            }

            RtspProbeResult(
                isOpen = true,
                port = port,
                responseCode = code,
                responseStatus = status,
                authType = authType,
                rawProtocol = proto,
                explanation = explanation
            )
        } catch (e: Exception) {
            val errMsg = when {
                e.message?.contains("refused", ignoreCase = true) == true ->
                    "Port $port is CLOSED on $host (Connection refused). The camera has no service listening on port $port."
                e.message?.contains("timed out", ignoreCase = true) == true ->
                    "Port $port TIMED OUT connecting to $host. Firewall or wrong IP."
                else ->
                    "Cannot connect to $host:$port: ${e.message}"
            }

            RtspProbeResult(
                isOpen = false,
                port = port,
                explanation = errMsg,
                error = e.javaClass.simpleName + ": " + (e.message ?: "Failed to connect")
            )
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
