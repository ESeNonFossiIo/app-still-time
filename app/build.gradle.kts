plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.comichao.stilltime"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.comichao.stilltime"
        minSdk = 26
        targetSdk = 36
        // A plain counter that `make bump` adds 1 to. Play remembers every code ever used, so never reuse or
        // lower it. See RELEASING.md before changing it.
        versionCode = 6
        versionName = "0.4.0"
    }

    signingConfigs {
        // Optional: keep the debug key next to the release key. Same defaults as Android's own.
        System.getenv("DEBUG_KEYSTORE")?.let { path -> getByName("debug") { storeFile = file(path) } }
        create("release") {
            storeFile = System.getenv("KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// APKs are named still-time-<versionName>-<variant>.apk
base.archivesName.set(provider { "still-time-${android.defaultConfig.versionName}" })

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
