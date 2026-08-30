# Add project specific ProGuard rules here.
# Vosk uses JNI native libraries — do not obfuscate them.
-keep class org.vosk.** { *; }
