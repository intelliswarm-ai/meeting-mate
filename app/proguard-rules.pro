# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Keep custom data classes
-keep class ai.intelliswarm.meetingmate.data.** { *; }
-keep class ai.intelliswarm.meetingmate.service.** { *; }
-keep class ai.intelliswarm.meetingmate.transcription.** { *; }

# Keep calendar service inner classes
-keep class ai.intelliswarm.meetingmate.service.CalendarService$* { *; }
-keep class ai.intelliswarm.meetingmate.data.MeetingFileManager$* { *; }

# OkHttp and Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.stream.** { *; }

# Room (if you add it later)
-dontwarn androidx.room.paging.**

# Security Crypto
-keep class androidx.security.crypto.** { *; }

# Dexter permissions
-keep class com.karumi.dexter.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}