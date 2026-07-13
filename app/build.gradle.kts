plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.synx.unlocker"
    compileSdk = 34

    // 签名配置 — CI 通过生成 keystore，本地如果有 keystore 也会自动签名
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("app/signing.properties")
            val keystoreFile = rootProject.file("app/debug.keystore")
            if (propsFile.exists() && keystoreFile.exists()) {
                val props = java.util.Properties()
                props.load(propsFile.inputStream())
                storeFile = keystoreFile
                storePassword = props.getProperty("storePassword", "android")
                keyAlias = props.getProperty("keyAlias", "debug")
                keyPassword = props.getProperty("keyPassword", "android")
            } else if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "debug"
                keyPassword = "android"
            }
        }
    }

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
            // 仅在 keystore 存在时应用签名配置
            if (rootProject.file("app/debug.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
