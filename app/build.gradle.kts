plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.synx.unlocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.synx.unlocker"
        minSdk = 26
        targetSdk = 34
        versionCode = 22700
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // Xposed API — compileOnly，只参与编译不打包进 APK
    compileOnly("de.robv.android.xposed:api:82")

    // Kotlin 标准库 — 需要打包进 APK
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
}
