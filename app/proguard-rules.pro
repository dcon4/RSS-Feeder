# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }

# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
