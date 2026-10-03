# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.idl.** { *** Companion; }
-keepclasseswithmembers class app.idl.** { kotlinx.serialization.KSerializer serializer(...); }
