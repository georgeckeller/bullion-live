import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bullionlive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bullionlive"
        minSdk = 26
        targetSdk = 34
        versionCode = 95
        versionName = "1.5.0"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        val finnhubKey = localProperties.getProperty("FINNHUB_API_KEY") ?: "YOUR_FINNHUB_API_KEY"
        buildConfigField("String", "FINNHUB_API_KEY", "\"$finnhubKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            // Enable both v1 (JAR) and v2 (APK) signing for maximum compatibility
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
