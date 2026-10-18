# Keep the JavaScript bridge methods
-keepclassmembers class com.smartcalc.app.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
