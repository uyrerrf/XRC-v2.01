# XRC core must survive R8 - all components are referenced by name in
# AndroidManifest, reflection, or the accessibility/device-admin bindings.
-keep class com.xrc.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable

# System-bound components
-keep class com.xrc.xrc.** { *; }
-keep class com.xrc.svc.** { *; }
-keep class com.xrc.receivers.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
