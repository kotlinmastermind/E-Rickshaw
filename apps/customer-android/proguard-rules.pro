# ProGuard / R8 Rules for Luxury Customer Android App (Play Store Production)

# Ensure attributes required for reflection / generic types are kept
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Kotlin Coroutines and Continuation (crucial for suspend functions in Retrofit)
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Retrofit Service Interfaces and Methods
-keep interface com.vaisentra.mobility.rider.core.network.CustomerApiService { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Network DTOs and Data Classes
-keep class com.vaisentra.mobility.rider.core.network.** { *; }
-keepclassmembers class com.vaisentra.mobility.rider.core.network.** { *; }

# Jetpack Compose
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Google Play Services Location & Mapbox Native SDK
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.location.** { *; }

# Common Android Core Library & Security
-keep class com.vaisentra.mobility.core.security.** { *; }
-keep class com.vaisentra.mobility.core.network.** { *; }
-keep class com.vaisentra.mobility.core.model.** { *; }



