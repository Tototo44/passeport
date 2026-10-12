plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Chaque construction sur GitHub augmente le numéro de version,
// pour que la nouvelle version s'installe par-dessus l'ancienne.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.passeport.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.passeport.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Seulement les processeurs de téléphones : l'APK est bien plus léger.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("passeport") {
            storeFile = file("passeport.jks")
            storePassword = "passeport"
            keyAlias = "passeport"
            keyPassword = "passeport"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("passeport")
        }
        debug {
            signingConfig = signingConfigs.getByName("passeport")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
}
