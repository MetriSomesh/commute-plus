# Commute+ ProGuard rules

# Retrofit
-keep class com.commuteplus.android.data.api.** { *; }
-keepclassmembers class com.commuteplus.android.data.api.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.commuteplus.android.**$$serializer { *; }
-keepclassmembers class com.commuteplus.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.commuteplus.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
