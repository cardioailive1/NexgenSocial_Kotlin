plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The Google Services plugin fails the build outright when
// google-services.json is absent, with an error that doesn't explain what to
// do about it. Applying it only when the file exists means the project builds
// without Firebase configured -- useful for a first run -- and prints a
// warning that says exactly what's missing and how to fix it.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        """
        |
        |  ============================================================
        |  google-services.json not found in app/
        |
        |  The app WILL build, but push notifications and incoming
        |  calls will not work until you add it:
        |
        |    1. console.firebase.google.com -> create or open a project
        |    2. Add app -> Android
        |    3. Package name must be exactly:
        |         com.corverxis.nexgensocial
        |    4. Download google-services.json into the app/ folder
        |       (next to build.gradle.kts, NOT inside app/src/)
        |
        |  See app/google-services.json.example for the expected shape.
        |  ============================================================
        |
        """.trimMargin()
    )
}

android {
    namespace = "com.corverxis.nexgensocial"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.corverxis.nexgensocial"
        minSdk = 26          // 26 covers ~95% of devices and gives us notification channels
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Provides Task.await(), used to get the FCM token as a suspend call.
    // Without this, `import kotlinx.coroutines.tasks.await` fails to resolve.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Media
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Encrypted token storage -- see TokenStore for why not plain prefs
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Push. The BOM pins compatible versions across all Firebase libraries,
    // so individual Firebase dependencies below deliberately carry no
    // version number -- the BOM decides.
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-messaging")

    // firebase-analytics is Firebase's default suggestion but is NOT
    // required for push. Left out on purpose: adding it means declaring
    // analytics collection in the Play Data Safety form, and this app
    // doesn't otherwise gather usage analytics. Uncomment if you want it.
    // implementation("com.google.firebase:firebase-analytics")

    // WebRTC for calls. See README-ANDROID.md -- the signalling is written,
    // the peer connection is not.
    implementation("io.github.webrtc-sdk:android:114.5735.02")
}
