plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Screens ki photo (screenshot) banane ke liye — design check karne mein kaam aata hai
    id("app.cash.paparazzi") version "1.3.4"
}

android {
    namespace = "com.chatcallguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chatcallguard"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}
