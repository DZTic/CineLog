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
# Type-safe Navigation routes (kotlinx.serialization)
# Navigation looks up each route's serializer reflectively (`INSTANCE.serializer()`
# for objects). kotlinx-serialization's bundled rule only keeps those members when
# INSTANCE is already used by the code, so a route that is registered in the
# NavHost but never instantiated (ScreenDestination.Search) crashed at startup.
# ------------------------------------------------------------------------------
-keepclassmembers @kotlinx.serialization.Serializable class com.example.** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------------------
# Retrofit 2 & OkHttp 3
# Retrofit (>= 2.10) and OkHttp ship their own R8 consumer rules, including the
# ones that keep the generic signatures of suspend service methods.
# ------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keepclassmembers class * {
    @retrofit2.http.** <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn okhttp3.internal.platform.**

# ------------------------------------------------------------------------------
# Moshi (JSON Serialization)
# Every model uses @JsonClass(generateAdapter = true): moshi-kotlin-codegen emits
# per-class rules (META-INF/proguard/moshi-*.pro) that keep each model's name and
# its generated JsonAdapter, and the moshi artifact ships the rest. Never use a
# global `-keep class * { ... }` here: it turns every class into an R8 seed.
# ------------------------------------------------------------------------------
-dontwarn com.squareup.moshi.**
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ------------------------------------------------------------------------------
# Coil (Image Loading)
# ------------------------------------------------------------------------------
-dontwarn coil.**
-dontwarn coil.compose.**
