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
