# Kanime R8 / ProGuard rules

# Keep Kotlin metadata and coroutines internals
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }

# Keep data models & plugin bridge/catalog types used across ContentProvider / JSON
-keep class com.kanime.app.data.model.** { *; }
-keep class com.kanime.app.data.plugin.** { *; }
-keep class com.kanime.app.data.api.WatchHistoryEntry { *; }
-keep class com.kanime.app.data.api.SkipSegment { *; }
-keep class com.kanime.app.data.api.EpisodeSkipTimes { *; }
-keep class com.kanime.app.ui.player.** { *; }

# Keep Parcelables and Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Media3 / ExoPlayer & GLSL effects
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil 3
-keep class coil3.** { *; }
-dontwarn coil3.**
