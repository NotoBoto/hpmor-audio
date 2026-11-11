# kotlinx.serialization keeps its own rules; keep our @Serializable models' companions
-keepclassmembers @kotlinx.serialization.Serializable class io.github.notoboto.unison.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.notoboto.unison.**$$serializer { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# sherpa-onnx (Piper voices): the native library reads these config classes and fields by name via JNI.
# Its AAR ships no consumer rules, so without this R8 renames them and the app crashes on the first synthesis.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# WorkManager's Room 2.6 database: Room keeps RoomDatabase subclasses but not their no-arg constructor, which it calls
# by reflection. R8 from AGP 9 no longer keeps that constructor implicitly, so without this the release build crashes
# at startup (NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
