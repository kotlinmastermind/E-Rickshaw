# Add project specific ProGuard rules here.
-keep class com.vaisentra.mobility.rider.data.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

