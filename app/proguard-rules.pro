# Keep ZXing / barcode scanner classes
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# Keep JavaScript interface methods (you have ZenBridge, ZenController)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}