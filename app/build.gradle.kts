plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.coreguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coreguard.app"
        minSdk = 26  // Android 8.0 — стабильная работа Work Profile
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile     = rootProject.file("CoreGuard-release.jks")
            storePassword = "CoreGuard_store_2026"
            keyAlias      = "CoreGuard"
            keyPassword   = "CoreGuard_store_2026"  // PKCS12: keypass == storepass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig   = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("com.google.zxing:core:3.5.3")
}
