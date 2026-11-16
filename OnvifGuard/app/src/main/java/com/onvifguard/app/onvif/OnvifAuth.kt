package com.onvifguard.app.onvif

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object OnvifAuth {

    fun generateSecurityHeader(username: String, password: String, timeOffsetMs: Long = 0): String {
        if (username.isBlank() && password.isBlank()) {
            return ""
        }

        val nonceBytes = ByteArray(16)
        SecureRandom().nextBytes(nonceBytes)
        val nonceBase64 = Base64.encodeToString(nonceBytes, Base64.NO_WRAP)

        // ISO 8601 UTC format: 2026-11-16T12:00:00Z
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val createdUtc = sdf.format(Date(System.currentTimeMillis() + timeOffsetMs))

        // Password_Digest = Base64( SHA-1( nonce + created + password ) )
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(nonceBytes)
        sha1.update(createdUtc.toByteArray(StandardCharsets.UTF_8))
        sha1.update(password.toByteArray(StandardCharsets.UTF_8))
        val digestBytes = sha1.digest()
        val passwordDigest = Base64.encodeToString(digestBytes, Base64.NO_WRAP)

        return """
        <s:Header>
            <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                           xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                <wsse:UsernameToken>
                    <wsse:Username>$username</wsse:Username>
                    <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$passwordDigest</wsse:Password>
                    <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonceBase64</wsse:Nonce>
                    <wsu:Created>$createdUtc</wsu:Created>
                </wsse:UsernameToken>
            </wsse:Security>
        </s:Header>
        """.trimIndent()
    }
}
