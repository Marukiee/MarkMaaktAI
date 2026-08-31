# MarkMaaktAI release rules.
#
# Most of the app needs nothing here: Compose, Room and Hilt all ship their own
# rules. What is listed below is the code that is reached from outside Kotlin, and
# which R8 therefore cannot see being used.

# MediaPipe and the LiteRT runtime call into their own native layer by name, and
# the option builders are reflected over. Stripping any of it fails at model load
# rather than at build time, which is the worst place to find out.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.framework.image.** { *; }
-dontwarn com.google.mediapipe.**

# Vosk speaks to libvosk through JNA, which resolves classes and fields by name.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# ML Kit's bundled text recogniser loads its model through the same pattern.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-dontwarn com.google.mlkit.**

# kotlinx.serialization generates serializers as companion members and looks them
# up reflectively. Without this the settings and summary parsing throw at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclasseswithmembers class **$serializer {
    static **$serializer INSTANCE;
}

# The services below are named in the manifest and instantiated by the system.
-keep class nl.markmaaktmedia.markmaaktai.service.** { *; }

# OkHttp pulls in optional platform integrations that are not present on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keeping line numbers makes a crash report from a user actually readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
