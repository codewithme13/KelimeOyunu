plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Google Services eklentisini uygula
    id("com.google.gms.google-services")
}

android {
    buildFeatures {
        viewBinding = true
    }
    namespace = "com.example.kelimeoyunu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kelimeoyunu"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.gridlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase bağımlılıkları
    implementation(platform("com.google.firebase:firebase-bom:32.5.0"))

    // Firebase Analytics (temel Firebase kitaplığı)
    implementation("com.google.firebase:firebase-analytics")

    // Realtime Database
    implementation("com.google.firebase:firebase-database")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")


    // Authentication
    implementation("com.google.firebase:firebase-auth")

    // Cloud Firestore
    implementation("com.google.firebase:firebase-firestore")

    // Cloud Storage
    implementation("com.google.firebase:firebase-storage")

    // Cloud Messaging
    implementation("com.google.firebase:firebase-messaging")
}