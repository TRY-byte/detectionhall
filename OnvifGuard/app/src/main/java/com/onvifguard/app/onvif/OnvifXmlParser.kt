package com.onvifguard.app.onvif

import com.onvifguard.app.data.MotionEvent
import com.onvifguard.app.data.MotionType
import java.util.regex.Pattern

data class OnvifProfile(val token: String, val name: String)

object OnvifXmlParser {

    fun parseMediaXAddr(xml: String): String? {
        val pattern = Pattern.compile("(?i)<[^:]*:?Media[^>]*>[\\s\\S]*?<[^:]*:?XAddr>([^<]+)</[^:]*:?XAddr>")
        val matcher = pattern.matcher(xml)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }

    fun parseEventsXAddr(xml: String): String? {
        val pattern = Pattern.compile("(?i)<[^:]*:?Events[^>]*>[\\s\\S]*?<[^:]*:?XAddr>([^<]+)</[^:]*:?XAddr>")
        val matcher = pattern.matcher(xml)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }

    fun parseProfiles(xml: String): List<OnvifProfile> {
        val profiles = mutableListOf<OnvifProfile>()
        // Match <trt:Profiles token="token_val"> ... <tt:Name>name_val</tt:Name>
        val profileBlockPattern = Pattern.compile("(?i)<[^:]*:?Profiles[^>]*token=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</[^:]*:?Profiles>")
        val namePattern = Pattern.compile("(?i)<[^:]*:?Name>([^<]+)</[^:]*:?Name>")

        val matcher = profileBlockPattern.matcher(xml)
        while (matcher.find()) {
            val token = matcher.group(1) ?: continue
            val content = matcher.group(2) ?: ""
            val nameMatcher = namePattern.matcher(content)
            val name = if (nameMatcher.find()) nameMatcher.group(1)?.trim() ?: token else token
            profiles.add(OnvifProfile(token, name))
        }
        return profiles
    }

    fun parseStreamUri(xml: String): String? {
        val pattern = Pattern.compile("(?i)<[^:]*:?Uri>([^<]+)</[^:]*:?Uri>")
        val matcher = pattern.matcher(xml)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }

    fun parseSubscriptionAddress(xml: String): String? {
        // Look inside SubscriptionReference for Address
        val pattern = Pattern.compile("(?i)<[^:]*:?SubscriptionReference[\\s\\S]*?<[^:]*:?Address>([^<]+)</[^:]*:?Address>")
        val matcher = pattern.matcher(xml)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }

        // Fallback: any Address tag
        val fallbackPattern = Pattern.compile("(?i)<[^:]*:?Address>([^<]+)</[^:]*:?Address>")
        val fallbackMatcher = fallbackPattern.matcher(xml)
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1)?.trim()
        }
        return null
    }

    fun parseMotionEvents(xml: String): List<MotionEvent> {
        val results = mutableListOf<MotionEvent>()

        // Split by NotificationMessage blocks
        val msgPattern = Pattern.compile("(?i)<[^:]*:?NotificationMessage[\\s\\S]*?</[^:]*:?NotificationMessage>")
        val msgMatcher = msgPattern.matcher(xml)

        while (msgMatcher.find()) {
            val block = msgMatcher.group()

            // Extract Topic
            val topicPattern = Pattern.compile("(?i)<[^:]*:?Topic[^>]*>([^<]+)</[^:]*:?Topic>")
            val topicMatcher = topicPattern.matcher(block)
            val topic = if (topicMatcher.find()) topicMatcher.group(1)?.trim() ?: "" else ""

            // Check if Topic is motion / analytics related
            val isMotionTopic = topic.contains("Motion", ignoreCase = true) ||
                    topic.contains("RuleEngine", ignoreCase = true) ||
                    topic.contains("VideoAnalytics", ignoreCase = true) ||
                    topic.contains("LineDetector", ignoreCase = true) ||
                    topic.contains("FieldDetector", ignoreCase = true) ||
                    topic.contains("Person", ignoreCase = true) ||
                    topic.contains("Human", ignoreCase = true)

            if (!isMotionTopic) continue

            // Determine State = true / 1
            // e.g. <tt:SimpleItem Name="State" Value="true"/> or Name="IsMotion" Value="true"
            val itemPattern = Pattern.compile("(?i)<[^:]*:?SimpleItem[^>]*Name=\"([^\"]+)\"[^>]*Value=\"([^\"]+)\"")
            val itemMatcher = itemPattern.matcher(block)

            var isTriggered = false
            var stateKey = ""

            while (itemMatcher.find()) {
                val name = itemMatcher.group(1) ?: ""
                val value = itemMatcher.group(2) ?: ""

                if (name.equals("State", ignoreCase = true) ||
                    name.equals("IsMotion", ignoreCase = true) ||
                    name.equals("Active", ignoreCase = true) ||
                    name.equals("Inside", ignoreCase = true) ||
                    name.equals("Detected", ignoreCase = true) ||
                    name.equals("IsInside", ignoreCase = true)
                ) {
                    stateKey = name
                    if (value.equals("true", ignoreCase = true) || value == "1") {
                        isTriggered = true
                    }
                }
            }

            if (isTriggered) {
                // Classify event type
                val type = when {
                    topic.contains("Person", ignoreCase = true) || topic.contains("Human", ignoreCase = true) -> MotionType.PERSON
                    topic.contains("Line", ignoreCase = true) -> MotionType.LINE_CROSSING
                    topic.contains("Field", ignoreCase = true) || topic.contains("Intrusion", ignoreCase = true) -> MotionType.INTRUSION
                    else -> MotionType.MOTION
                }

                val details = when (type) {
                    MotionType.PERSON -> "Person detected entering monitored zone"
                    MotionType.LINE_CROSSING -> "Perimeter line crossing detected"
                    MotionType.INTRUSION -> "Intrusion detected in security field"
                    else -> "Motion activity detected ($stateKey: active)"
                }

                results.add(
                    MotionEvent(
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        topic = topic,
                        details = details
                    )
                )
            }
        }

        return results
    }
}
