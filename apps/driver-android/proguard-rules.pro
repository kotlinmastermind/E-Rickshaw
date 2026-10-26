# ProGuard / R8 Rules for Luxury Driver Android App (Play Store Production)

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
-keep interface com.luxury.chauffeur.driver.core.network.DriverApiService { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Foreground Location Service
-keep class com.luxury.chauffeur.driver.core.location.** { *; }

# Gson
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Network DTOs and Data Classes
-keep class com.luxury.chauffeur.driver.core.network.** { *; }
-keepclassmembers class com.luxury.chauffeur.driver.core.network.** { *; }

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

# Firebase
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

