# ProGuard rules for DriverGo
-keep class com.vaisentra.mobility.driver.data.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

