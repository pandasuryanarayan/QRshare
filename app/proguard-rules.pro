# ZXing has no reflection-based lookup in the paths we use, but its result
# parsers are referenced by name in some builds; keep the public surface.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep our activity entry points reachable from the manifest.
-keep class com.wchung.qrshare.MainActivity { *; }
-keep class com.wchung.qrshare.ScanActivity { *; }
-keep class com.wchung.qrshare.HistoryActivity { *; }
-keep class com.wchung.qrshare.QrTileService { *; }

# Keep annotations that R8 would otherwise strip from the manifest merge.
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
