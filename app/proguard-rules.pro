# R8 rules for the launcher.
#
# The app uses no reflection of its own; Compose and AndroidX ship their own
# consumer rules, so the defaults from proguard-android-optimize.txt are enough.
# Keep line numbers useful in any crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
