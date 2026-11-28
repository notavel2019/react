# ProGuard / R8 rules for Google Play Release builds (AAB / APK)

# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }
-keep interface com.android.billingclient.api.** { *; }

# Keep source file and line numbers for Play Console crash symbolication
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 Transformer / ExoPlayer reflection-loaded components
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# CameraX camera2 interop
-keep class androidx.camera.camera2.** { *; }

# ML Kit Selfie Segmentation (on-device person cutout)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Firebase Firestore data models
-keep class com.example.firebase.** { *; }

