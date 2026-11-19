# HMR Proguard / R8 rules
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.uniqueselect.hmr.KsuBridge { *; }
-keep class com.uniqueselect.hmr.MainActivity { *; }
