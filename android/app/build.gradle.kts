import java.util.Properties
import java.net.URI

// Offline voice engine (sherpa-onnx with Piper phonemizer). Not stored in git: fetched once from the official release.
// It is not packed into the APK (~30 MB, unchanged between app versions): `gradlew :app:piperEngine` makes
// build/piper-engine/piper-engine-<abi>.zip, which go to the GitHub release named in Piper.ENGINE_TAG. After rebuilding
// them, update the SHA-256 sums in Piper.kt.
val sherpaAar = file("libs/sherpa-onnx.aar")
if (!sherpaAar.exists()) {
    sherpaAar.parentFile.mkdirs()
    URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar").toURL()
        .openStream().use { i: java.io.InputStream -> sherpaAar.outputStream().use { o -> i.copyTo(o) } }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing: keystore lives outside the repo (~/.unison/keystore.properties)
val signProps = Properties().apply {
    val f = File(System.getProperty("user.home"), ".unison/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.notoboto.unison"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.notoboto.unison"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.2.1"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (signProps.isNotEmpty()) create("release") {
            storeFile = File(signProps.getProperty("storeFile"))
            storePassword = signProps.getProperty("storePassword")
            keyAlias = signProps.getProperty("keyAlias")
            keyPassword = signProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        debug { applicationIdSuffix = ".debug"; ndk { abiFilters += listOf("x86_64") } }   // + emulator
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("androidx.media3:media3-cast:1.6.1")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // offline neural voices (Piper): the Kotlin wrapper lives in src (com.k2fsa.sherpa.onnx), the native engine is
    // downloaded by the app on first use — see the piperEngine task below and data/Piper.kt
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

// Native Piper engine packages for the app to download: only the two libraries the JNI wrapper needs
// (libsherpa-onnx-c-api / -cxx-api are separate C/C++ APIs the app never loads).
val piperEngineZips = listOf("arm64-v8a", "x86_64").map { abi ->
    tasks.register<Zip>("piperEngine-$abi") {
        from(zipTree(sherpaAar)) { include("jni/$abi/libonnxruntime.so", "jni/$abi/libsherpa-onnx-jni.so"); eachFile { path = name } }
        includeEmptyDirs = false
        isPreserveFileTimestamps = false; isReproducibleFileOrder = true
        archiveFileName.set("piper-engine-$abi.zip")
        destinationDirectory.set(layout.buildDirectory.dir("piper-engine"))
    }
}
tasks.register("piperEngine") { group = "unison"; dependsOn(piperEngineZips) }
