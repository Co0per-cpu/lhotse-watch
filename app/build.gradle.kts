plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.coopercpu.reloj"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coopercpu.reloj"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        // El kit de IDO trae librerías nativas solo para estas arquitecturas
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Llave fija (de uso personal) para que cada versión nueva se instale encima de la anterior
    signingConfigs {
        create("personal") {
            storeFile = file("firma.keystore")
            storePassword = "relojlhotse"
            keyAlias = "reloj"
            keyPassword = "relojlhotse"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

val flutterEngine = "1.0.0-36335019a8eab588c3c2ea783c618d90505be233"

dependencies {
    // Kit oficial IDO (licencia MIT): github.com/idoosmart/android_sdk
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation("io.flutter:flutter_embedding_release:$flutterEngine")
    implementation("io.flutter:arm64_v8a_release:$flutterEngine")
    implementation("io.flutter:armeabi_v7a_release:$flutterEngine")
    implementation("no.nordicsemi.android:dfu:2.5.0")
    implementation("no.nordicsemi.android:mcumgr-ble:1.5.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
