import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is read from keystore.properties (not committed). See BUILD.md.
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
// -PversionCodeOverride / -PversionNameOverride exist only for update testing.
val appVersionCode = (findProperty("versionCodeOverride") as String?)?.toInt() ?: versionProps.getProperty("VERSION_CODE").toInt()
val appVersionName = (findProperty("versionNameOverride") as String?) ?: versionProps.getProperty("VERSION_NAME")

// GitHub repository that hosts releases. The in-app updater reads its public release list (no token).
val updateRepo = "ramyadmin-BYD/RDM-Eq"

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.rdm.eq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rdm.eq"
        // DynamicsProcessing (the main DSP engine) needs API 28. DiLink 5.x is Android 13 (API 33).
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "UPDATE_API_BASE", "\"https://api.github.com\"")
        buildConfigField("String", "UPDATE_METADATA_ASSET", "\"rdm-eq-update.json\"")
        // Debug builds may point the updater at a local test server (see app/src/debug).
        buildConfigField("boolean", "UPDATE_ALLOW_TEST_SOURCE", "false")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "UPDATE_ALLOW_TEST_SOURCE", "true")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
}
