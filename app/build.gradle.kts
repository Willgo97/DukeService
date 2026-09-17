import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "nl.dejongduke.service"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.dejongduke.service"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "3.5"
    }

    // Release signing comes from ~/.gradle/gradle.properties (or the matching
    // environment variables), never from the repository. Without them the build
    // still works and simply falls back to the debug key.
    val releaseStore = (findProperty("DUKE_STORE_FILE") as String?)
        ?: System.getenv("DUKE_STORE_FILE")
    val releaseSigning = if (releaseStore != null && file(releaseStore).exists()) {
        signingConfigs.create("release") {
            storeFile = file(releaseStore)
            storePassword = (findProperty("DUKE_STORE_PASSWORD") as String?)
                ?: System.getenv("DUKE_STORE_PASSWORD")
            keyAlias = (findProperty("DUKE_KEY_ALIAS") as String?)
                ?: System.getenv("DUKE_KEY_ALIAS")
            keyPassword = (findProperty("DUKE_KEY_PASSWORD") as String?)
                ?: System.getenv("DUKE_KEY_PASSWORD")
        }
    } else {
        logger.lifecycle("No release keystore configured; signing with the debug key.")
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The bundled OCR library is ~11 MB of native code per architecture, and
    // shipping all four quadruples the download for no one's benefit. Phones
    // get arm64, the emulator gets x86_64.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    androidResources {
        // The catalog assets are already compact JSON; compressing them again
        // only slows down the cold start where every millisecond is visible.
        noCompress += "json"
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/*.version")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.animation:animation")

    // Camera + on-device text recognition for the scanner. The bundled ML Kit
    // model keeps it working without a network, which is the whole point.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Reads the bundled assets on the JVM, so a mismatch between the generated
    // data and the models shows up here instead of on the first screen.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
