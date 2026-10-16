plugins {
    id("com.android.application") version "8.1.1" apply false
    kotlin("android") version "1.9.20" apply false
}

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "mz.nyisay.virtualracingwheel"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
}
