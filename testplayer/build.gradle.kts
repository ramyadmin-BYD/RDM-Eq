// Tiny stand-in for a third-party media app (e.g. a local music player) used to verify
// RDM Eq's cross-app processing on an emulator. Not part of the RDM Eq APK.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.rdm.testplayer"
    compileSdk = 35
    defaultConfig { applicationId = "com.rdm.testplayer"; minSdk = 28; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
