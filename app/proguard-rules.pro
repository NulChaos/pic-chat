# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Wi-Fi Direct APIs
-keep class android.net.wifi.p2p.** { *; }

# Keep model classes for serialization
-keep class com.nulchaos.pichat.model.** { *; }
-keep enum com.nulchaos.pichat.model.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Retain method names for reflection
-keepclassmembernames,allowobfuscation class ** {
    long composeContext;
}
