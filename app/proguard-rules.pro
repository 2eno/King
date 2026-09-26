# Game model classes are (de)serialized with kotlinx.serialization and sealed-class discriminators.
-keep class eu.zipf.zeno.monstermayor.core.** { *; }
-keep class eu.zipf.zeno.monstermayor.client.SessionIdentity { *; }

# Ktor / OkHttp optional dependencies that are not present on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
