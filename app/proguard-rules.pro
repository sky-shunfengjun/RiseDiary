# Room
-keep class com.risediary.app.data.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.risediary.app.**$$serializer { *; }
-keepclassmembers class com.risediary.app.** { *** Companion; }
-keepclasseswithmembers class com.risediary.app.** { kotlinx.serialization.KSerializer serializer(...); }
