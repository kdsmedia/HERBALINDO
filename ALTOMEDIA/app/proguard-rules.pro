# Keep Firestore model classes (reflection-based deserialization)
-keepclassmembers class com.altomedia.herbalindo.data.model.** {
    *;
}
-keep class com.altomedia.herbalindo.data.model.** { *; }

# Firebase
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Auth internal
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.internal.** { *; }

# Google Play Ads
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# AndroidX / Material
-dontwarn androidx.**

# Keep generic signatures used by Firestore converters
-keepattributes InnerClasses,EnclosingMethod

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}