# Huawei Health Kit
-keep class com.huawei.hms.health.** { *; }
-keep class com.huawei.hms.support.account.** { *; }
-keep class com.huawei.hms.common.** { *; }
-keep class com.huawei.agconnect.** { *; }

# HMS Core
-keep class com.huawei.hms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
