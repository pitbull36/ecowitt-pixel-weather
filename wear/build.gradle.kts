import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.dariopittera.ecowittpixel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dariopittera.ecowittpixel"
        minSdk = 30
        targetSdk = 35
        versionCode = 3026
        versionName = "26.0.0"
        buildConfigField("String", "GITHUB_REPO", "\"pitbull36/ecowitt-pixel-weather\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
                ?: System.getenv("KEYSTORE_FILE")
                ?: "../release-keystore.jks"
            storeFile = file(storeFilePath)
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: "pixelota123"
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: System.getenv("KEY_ALIAS")
                ?: "pixelota"
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: System.getenv("KEY_PASSWORD")
                ?: "pixelota123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                "../app/src/main/java/com/dariopittera/ecowittpixel/model",
                "../app/src/main/java/com/dariopittera/ecowittpixel/data"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Wear OS Compose, Ambient Mode, Tiles, ProtoLayout, Complications, and Data Layer
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha29")
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.2.1-android")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
