# ML Kit (text recognition, bundled Japanese model) creates its components by reflection
# and talks to its native library. R8 full mode (AGP 9 default) otherwise optimizes these
# internals into a broken state: v0.1.0 failed every scan with a null field read inside
# com.google.mlkit.vision.text.internal. CI checks that no ML Kit class is renamed.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.datatransport.** { *; }
