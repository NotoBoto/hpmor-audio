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
