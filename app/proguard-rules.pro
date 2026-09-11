# kotlinx.serialization keeps its generated serializers on the companion objects;
# without these the release build fails to parse the bundled catalog.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class nl.dejongduke.service.data.** {
    *** Companion;
}
-keepclasseswithmembers class nl.dejongduke.service.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class nl.dejongduke.service.data.**$$serializer { *; }
