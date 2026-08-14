# ==============================================================================
# ProGuard / R8 Rules for CinéLog
# ==============================================================================

# ------------------------------------------------------------------------------
# General / Kotlin Coroutines & Reflection
# ------------------------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.IntellijIdeaDebugDetector
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn java.lang.ClassValue

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# AndroidX & Jetpack Compose
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# ProfileInstaller
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**

# ------------------------------------------------------------------------------
# Room Database & SQLite
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.migration.Migration { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}

# ------------------------------------------------------------------------------
# Retrofit 2 & OkHttp 3
# ------------------------------------------------------------------------------
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepclassmembers class * {
    @retrofit2.http.** <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn okhttp3.internal.platform.**

# ------------------------------------------------------------------------------
# Moshi (JSON Serialization)
# ------------------------------------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ------------------------------------------------------------------------------
# Coil (Image Loading)
# ------------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**
-dontwarn coil.compose.**

# ------------------------------------------------------------------------------
# CineLog Data Layer Models
# ------------------------------------------------------------------------------
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }
-keep class * implements java.io.Serializable { *; }
