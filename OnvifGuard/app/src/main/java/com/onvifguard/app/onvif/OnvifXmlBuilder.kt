package com.onvifguard.app.onvif

object OnvifXmlBuilder {

    fun wrapSoapEnvelope(header: String, body: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
            xmlns:tt="http://www.onvif.org/ver10/schema"
            xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
            xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
            xmlns:tev="http://www.onvif.org/ver10/events/wsdl"
            xmlns:wsnt="http://docs.oasis-open.org/wsn/b-2"
            xmlns:wsa="http://www.w3.org/2005/08/addressing">
    $header
    <s:Body>
        $body
    </s:Body>
</s:Envelope>
""".trimIndent()
    }

    fun getCapabilities(): String {
        return """
        <tds:GetCapabilities>
            <tds:Category>All</tds:Category>
        </tds:GetCapabilities>
        """.trimIndent()
    }

    fun getDeviceInformation(): String {
        return """
        <tds:GetDeviceInformation />
        """.trimIndent()
    }

    fun getProfiles(): String {
        return """
        <trt:GetProfiles />
        """.trimIndent()
    }

    fun getStreamUri(profileToken: String): String {
        return """
        <trt:GetStreamUri>
            <trt:StreamSetup>
                <tt:Stream>RTP-Unicast</tt:Stream>
                <tt:Transport>
                    <tt:Protocol>RTSP</tt:Protocol>
                </tt:Transport>
            </trt:StreamSetup>
            <trt:ProfileToken>$profileToken</trt:ProfileToken>
        </trt:GetStreamUri>
        """.trimIndent()
    }

    fun createPullPointSubscription(terminationMinutes: Int = 10): String {
        return """
        <tev:CreatePullPointSubscription>
            <tev:InitialTerminationTime>PT${terminationMinutes}M</tev:InitialTerminationTime>
        </tev:CreatePullPointSubscription>
        """.trimIndent()
    }

    fun pullMessages(timeoutSeconds: Int = 3, messageLimit: Int = 10): String {
        return """
        <tev:PullMessages>
            <tev:Timeout>PT${timeoutSeconds}S</tev:Timeout>
            <tev:MessageLimit>$messageLimit</tev:MessageLimit>
        </tev:PullMessages>
        """.trimIndent()
    }

    fun renew(terminationMinutes: Int = 10): String {
        return """
        <wsnt:Renew>
            <wsnt:TerminationTime>PT${terminationMinutes}M</wsnt:TerminationTime>
        </wsnt:Renew>
        """.trimIndent()
    }

    fun unsubscribe(): String {
        return """
        <wsnt:Unsubscribe />
        """.trimIndent()
    }
}
