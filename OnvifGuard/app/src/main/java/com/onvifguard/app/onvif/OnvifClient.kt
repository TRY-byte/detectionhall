package com.onvifguard.app.onvif

import com.onvifguard.app.data.CameraConfig
import com.onvifguard.app.data.MotionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OnvifClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Dedicated client for PullMessages long polling with variable timeout
    private fun getPollingClient(timeoutSeconds: Int): OkHttpClient {
        return httpClient.newBuilder()
            .readTimeout((timeoutSeconds + 5).toLong(), TimeUnit.SECONDS)
            .build()
    }

    private val soapMediaType = "application/soap+xml; charset=utf-8".toMediaType()

    suspend fun testAndInitialize(config: CameraConfig): Result<CameraConfig> = withContext(Dispatchers.IO) {
        try {
            val deviceUrl = config.getDeviceServiceUrl()

            // Step 1: Get Capabilities
            val capHeader = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val capBody = OnvifXmlBuilder.getCapabilities()
            val capEnvelope = OnvifXmlBuilder.wrapSoapEnvelope(capHeader, capBody)

            val capRespXml = sendSoapRequest(deviceUrl, capEnvelope)
            val mediaUrl = OnvifXmlParser.parseMediaXAddr(capRespXml)
                ?: "http://${config.host}:${config.port}/onvif/media_service"
            val eventUrl = OnvifXmlParser.parseEventsXAddr(capRespXml)
                ?: "http://${config.host}:${config.port}/onvif/event_service"

            // Step 2: Get Profiles
            val profHeader = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val profBody = OnvifXmlBuilder.getProfiles()
            val profEnvelope = OnvifXmlBuilder.wrapSoapEnvelope(profHeader, profBody)

            val profRespXml = sendSoapRequest(mediaUrl, profEnvelope)
            val profiles = OnvifXmlParser.parseProfiles(profRespXml)

            if (profiles.isEmpty()) {
                return@withContext Result.failure(Exception("No video profiles found on camera"))
            }

            // Prefer sub-stream profile for faster low-latency network streaming, otherwise first profile
            val chosenProfile = profiles.find { it.name.contains("sub", ignoreCase = true) }
                ?: profiles.first()

            // Step 3: Get Stream URI
            val streamHeader = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val streamBody = OnvifXmlBuilder.getStreamUri(chosenProfile.token)
            val streamEnvelope = OnvifXmlBuilder.wrapSoapEnvelope(streamHeader, streamBody)

            val streamRespXml = sendSoapRequest(mediaUrl, streamEnvelope)
            var rawStreamUri = OnvifXmlParser.parseStreamUri(streamRespXml)
                ?: "rtsp://${config.host}:${config.rtspPort}/live"

            // Format RTSP URL with embedded credentials if not present
            val finalRtspUrl = if (config.username.isNotBlank() && !rawStreamUri.contains("@")) {
                if (rawStreamUri.startsWith("rtsp://", ignoreCase = true)) {
                    "rtsp://${config.username}:${config.password}@" + rawStreamUri.substring(7)
                } else {
                    rawStreamUri
                }
            } else {
                rawStreamUri
            }

            val updatedConfig = config.copy(
                mediaServiceUrl = mediaUrl,
                eventServiceUrl = eventUrl,
                profileToken = chosenProfile.token,
                rtspUrl = finalRtspUrl
            )

            Result.success(updatedConfig)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPullPointSubscription(config: CameraConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val eventUrl = if (config.eventServiceUrl.isNotBlank()) {
                config.eventServiceUrl
            } else {
                "http://${config.host}:${config.port}/onvif/event_service"
            }

            val header = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val body = OnvifXmlBuilder.createPullPointSubscription(terminationMinutes = 10)
            val envelope = OnvifXmlBuilder.wrapSoapEnvelope(header, body)

            val respXml = sendSoapRequest(eventUrl, envelope)
            val subAddress = OnvifXmlParser.parseSubscriptionAddress(respXml)

            if (subAddress.isNullOrBlank()) {
                Result.failure(Exception("Failed to obtain Subscription Reference from camera event service"))
            } else {
                // If subscription address is relative or has different host, resolve it
                val resolvedSubUrl = if (subAddress.startsWith("http://", ignoreCase = true) || subAddress.startsWith("https://", ignoreCase = true)) {
                    subAddress
                } else {
                    "http://${config.host}:${config.port}/$subAddress".replace("//", "/").replace("http:/", "http://")
                }
                Result.success(resolvedSubUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullEvents(
        subscriptionUrl: String,
        config: CameraConfig,
        timeoutSeconds: Int = 3
    ): Result<List<MotionEvent>> = withContext(Dispatchers.IO) {
        try {
            val header = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val body = OnvifXmlBuilder.pullMessages(timeoutSeconds = timeoutSeconds, messageLimit = 10)
            val envelope = OnvifXmlBuilder.wrapSoapEnvelope(header, body)

            val client = getPollingClient(timeoutSeconds)
            val request = Request.Builder()
                .url(subscriptionUrl)
                .post(envelope.toRequestBody(soapMediaType))
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 200) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("PullMessages HTTP ${response.code}: $errBody"))
                }

                val xml = response.body?.string() ?: ""
                val events = OnvifXmlParser.parseMotionEvents(xml)
                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renewSubscription(subscriptionUrl: String, config: CameraConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val header = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val body = OnvifXmlBuilder.renew(terminationMinutes = 10)
            val envelope = OnvifXmlBuilder.wrapSoapEnvelope(header, body)

            val request = Request.Builder()
                .url(subscriptionUrl)
                .post(envelope.toRequestBody(soapMediaType))
                .build()

            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unsubscribe(subscriptionUrl: String, config: CameraConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val header = OnvifAuth.generateSecurityHeader(config.username, config.password)
            val body = OnvifXmlBuilder.unsubscribe()
            val envelope = OnvifXmlBuilder.wrapSoapEnvelope(header, body)

            val request = Request.Builder()
                .url(subscriptionUrl)
                .post(envelope.toRequestBody(soapMediaType))
                .build()

            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun sendSoapRequest(url: String, envelope: String): String {
        val request = Request.Builder()
            .url(url)
            .post(envelope.toRequestBody(soapMediaType))
            .header("Content-Type", "application/soap+xml; charset=utf-8")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("SOAP call to $url failed with HTTP ${response.code}: $errBody")
            }
            return response.body?.string() ?: ""
        }
    }
}
