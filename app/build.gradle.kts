plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.cefrspeakingcoach.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cefrspeakingcoach.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Uncomment when adding sherpa-onnx. The AAR ships arm64-v8a,
        // armeabi-v7a, x86 and x86_64; dropping x86 halves the APK and costs
        // nothing on real devices, but keep x86_64 for the emulator.
        // ndk {
        //     abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        // }
    }

    androidResources {
        // ONNX Runtime memory-maps the model file and reads multi-byte values at
        // aligned offsets. aapt compresses assets/ by default, which breaks that
        // alignment and crashes at load with SIGBUS / BUS_ADRALN inside
        // libonnxruntime.so. Keeping these extensions uncompressed is the fix.
        // Applies whether or not sherpa is wired up yet, so it is safe to leave on.
        noCompress += listOf("onnx", "bin", "tokens", "fst", "far")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    // Firebase.
    // The BoM MUST come first: it sets the version for every module below it.
    // No Firebase module may carry its own version, in the TOML or here. A
    // per-module version (firebase-crashlytics used to pin 20.0.4) desyncs BoM
    // resolution and leaves the unversioned ones, like firebase-ai, resolving
    // to an empty version. That was the build failure.
    // Version lives once, in firebaseBom in libs.versions.toml.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.crashlytics)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Credentials API for Google Sign-In

    // ---------------------------------------------------------------------
    // sherpa-onnx, offline neural TTS. See docs/sherpa/SHERPA_INTEGRATION.md
    //
    // Served through JitPack (its jitpack.yml installs the prebuilt Android
    // AAR with native libs for arm64-v8a, armeabi-v7a, x86, x86_64). No manual
    // .aar download needed.
    //
    // STEP 1 (this build): confirm this line resolves. If Gradle reports
    // "Could not find com.github.k2-fsa:sherpa-onnx", tell me and we switch to
    // the manual AAR from the GitHub release instead.
    // ---------------------------------------------------------------------
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
