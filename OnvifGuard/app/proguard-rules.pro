# Proguard rules for ONVIF Guard
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class androidx.media3.** { *; }
