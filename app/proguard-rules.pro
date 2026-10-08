# Glyph SDK talks to the system service via AIDL/reflection-free calls; keep it intact.
-keep class com.nothing.** { *; }
-dontwarn com.nothing.**
