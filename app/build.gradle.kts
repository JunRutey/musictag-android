import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Signing.
 *
 * CI decodes the release keystore from secrets before invoking Gradle and exports
 * MUSICTAG_KEYSTORE_PATH / MUSICTAG_KEYSTORE_PASSWORD / MUSICTAG_KEY_ALIAS /
 * MUSICTAG_KEY_PASSWORD. Local builds without those variables fall back to the
 * debug key so `assembleDebug` never requires secrets.
 */
val keystorePath: String? = System.getenv("MUSICTAG_KEYSTORE_PATH")

android {
    namespace = "com.xjcheng.musictageditor"
    compileSdk = 35

    // Pinned NDK: the tag engine compiles upstream TagLib for three ABIs.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.xjcheng.musictageditor"
        // Android 7.0. Miuix 0.7.2 (the newest release the pinned Kotlin 2.2.0 compiler can
        // consume) declares minSdk 24, and the library harness has no override for that.
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 11
        versionName = (project.findProperty("versionName") as String?) ?: "1.1.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // x86 is dead weight for a sideloaded utility; arm64 + armv7 + emulator x86_64.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // Static libc++ keeps the APK self-contained (no libc++_shared.so entry).
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../native/taglib/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MUSICTAG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MUSICTAG_KEY_ALIAS")
                keyPassword = System.getenv("MUSICTAG_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Deliberately identical applicationId: the compatibility contract and the
            // compat-harness tests must exercise the same package as release builds.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // CI builds the release APK unsigned (-Pmusictag.unsignedRelease=true) and signs
            // it with apksigner, because the keystore never leaves the secrets store. Local
            // builds without a release signing config still fall back to the debug key.
            val unsignedRelease = (findProperty("musictag.unsignedRelease") as String?)?.toBoolean() == true
            signingConfig = when {
                unsignedRelease -> null
                else -> signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core:tag"))
    implementation(project(":core:net"))
    implementation(project(":core:lyrics"))
    implementation(libs.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // MIUI/HyperOS design language. Pinned to the last Kotlin 2.2-compatible release.
    implementation(libs.miuix)
    implementation(libs.pinyin4j)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
