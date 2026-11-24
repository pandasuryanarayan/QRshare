plugins {
    alias(libs.plugins.android.application)
}

// Release signing is provided by CI through environment variables so that no
// keystore or password ever lives in the repository.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasKeystore: Boolean = keystorePath != null && file(keystorePath).exists()

// A release built without the shared key cannot install over an existing app,
// so CI fails loudly instead of shipping one.
val requireSigning: Boolean = System.getenv("REQUIRE_SIGNING") == "true"

android {
    namespace = "com.wchung.qrshare"
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.wchung.qrshare"
        minSdk = 21
        targetSdk = 36
        versionCode = 25
        versionName = "1.1.2"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel = "FULL"
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "DebugProbesKt.bin",
                "kotlin/**"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

if (requireSigning && !hasKeystore) {
    throw GradleException(
        "REQUIRE_SIGNING is set but no keystore was found at KEYSTORE_PATH. " +
            "Every release must be signed with the same key."
    )
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.zxing.core)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    testImplementation(libs.junit)
}
