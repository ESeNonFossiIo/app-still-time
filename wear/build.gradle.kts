plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One version for phone and watch: read it from the phone module so `make bump` covers both.
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val appVersionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(appGradle)!!.groupValues[1]
val appVersionCode = Regex("""versionCode\s*=\s*(\d+)""").find(appGradle)!!.groupValues[1].toInt()
// Version code of the watch bundle uploaded to Play: the phone's code plus 1000. See RELEASING.md.
val playWearVersionCode = appVersionCode + 1_000

android {
    namespace = "org.comichao.stilltime.wear"
    compileSdk = 37

    defaultConfig {
        // Same id as the phone app, so the Data Layer pairs the two.
        applicationId = "org.comichao.stilltime"
        minSdk = 30
        targetSdk = 37
        // Play wants a different version code per form factor, so bundles for Play (-PplayBundle) use the
        // phone's code plus 1000. The APK on GitHub keeps the phone's code. See RELEASING.md.
        versionCode = if (project.hasProperty("playBundle")) playWearVersionCode else appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        System.getenv("DEBUG_KEYSTORE")?.let { path -> getByName("debug") { storeFile = file(path) } }
        create("release") {
            storeFile = System.getenv("KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            // -PidSuffix=.debug installs a copy next to the Play one (its own id and data); give the phone the same suffix.
            (project.findProperty("idSuffix") as String?)?.let { applicationIdSuffix = it }
        }
        release { signingConfig = signingConfigs.getByName("release") }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// APKs are named still-time-wear-<versionName>-<variant>.apk
base.archivesName.set("still-time-wear-$appVersionName")

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.7.1")
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Play flags fragment 1.1.0 as outdated; play-services-wearable pulls it in transitively, so pin a current one.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.wear:wear:1.4.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
