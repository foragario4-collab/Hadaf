plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gebetszeiten.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gebetszeiten.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 59
        versionName = "6.3.1"
        // Nummer des GitHub-Builds (für das In-App-Update)
        buildConfigField("int", "BUILD_RUN", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("fest") {
            // Nur der private Schlüssel aus den GitHub-Secrets – kein Ersatz-Schlüssel im Projekt.
            // Fehlt er, bricht der Build ab, statt mit einem unsicheren Schlüssel zu signieren.
            val envStore = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
                ?: throw GradleException("Signierschlüssel fehlt: GitHub-Secret SIGNING_KEYSTORE_B64 eintragen")
            storeFile = file(envStore)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: throw GradleException("GitHub-Secret SIGNING_STORE_PASSWORD fehlt")
            keyAlias = "gebetszeiten"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: throw GradleException("GitHub-Secret SIGNING_KEY_PASSWORD fehlt")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fest")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fest")
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.maplibre.gl:android-sdk:11.5.2")
}
