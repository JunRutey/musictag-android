# Release shrinker rules.
#
# Manifest-referenced components (activities, services, providers) are kept
# automatically by AGP, so the compatibility surface needs no manual rule here.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JNI bridge (M1, core/tag). Native method names are resolved by name from C++.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep annotated Compose-friendly models readable in crash reports.
-keep class com.xjcheng.musictageditor.compat.** { *; }

# R8 full mode (android.enableR8.fullMode=true) uses aggressive optimization.
# Providers parse JsonObject/JsonElement trees directly - there are no
# @Serializable classes and no reflection - so serialization needs no broad
# keep rule and stays fully shrinkable. Keep annotation metadata for Compose,
# and silence library warnings that full mode surfaces.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.serialization.**
