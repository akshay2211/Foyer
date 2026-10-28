# R8 rules for the launcher.
#
# The app uses no reflection of its own; Compose and AndroidX ship their own
# consumer rules, so the defaults from proguard-android-optimize.txt are enough.
# Keep line numbers useful in any crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MediaPipe tasks-genai references compile-time-only annotations (AutoValue,
# protobuf build annotations) that are absent at runtime by design.
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoPresenceBits
