# Modellklassen werden per kotlinx.serialization und Room gelesen
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class de.mediclever.tagebuch.core.model.**$$serializer { *; }
-keepclassmembers class de.mediclever.tagebuch.core.model.** { *** Companion; }
-keepclasseswithmembers class de.mediclever.tagebuch.core.model.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
