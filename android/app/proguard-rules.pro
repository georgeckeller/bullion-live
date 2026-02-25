# BullionLive ProGuard/R8 Rules
-keepattributes Signature
-keepattributes *Annotation*

# Keep @JavascriptInterface methods for WebView bridge
# These methods are called from JavaScript and must not be renamed or removed
-keepclassmembers class com.bullionlive.MainActivity$CacheBridge {
    @android.webkit.JavascriptInterface *;
}

# Keep AppConfig constants (referenced by name in some contexts)
-keep class com.bullionlive.data.AppConfig { *; }
