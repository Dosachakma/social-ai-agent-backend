# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve Line Numbers for Crash Reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Data Models & Moshi JSON serialization
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.data.ai.** { *; }
-keep class com.example.data.config.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Retrofit / OkHttp
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# BuildConfig
-keep class com.example.BuildConfig { *; }

