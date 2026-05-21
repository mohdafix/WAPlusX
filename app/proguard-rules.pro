# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontwarn *

# (R fields are accessed and rewritten via reflection)
-keep class com.wmods.wppenhacer.R { *; }
-keep class com.wmods.wppenhacer.R$* { *; }
-keepclassmembers class com.wmods.wppenhacer.R$* {
     public static <fields>;
}

# Keep all com.wmods classes and members to prevent obfuscation/shrinking in the Xposed module
-keep class com.wmods.** { *; }
-keepclassmembers class com.wmods.** { *; }

-keep class cz.vutbr.** { *; }

-keep class com.assemblyai.api.** { *; }

# Keep PreferenceManager and prevent method inlining so that the Xposed hook on getDefaultSharedPreferencesMode works in release builds
-keep class androidx.preference.PreferenceManager { *; }

# Keep DexKit classes and members
-keep class org.luckypray.dexkit.** { *; }
-keepclassmembers class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep source file names and line numbers for better retracing and runtime stacktrace inspection
-keepattributes SourceFile,LineNumberTable


