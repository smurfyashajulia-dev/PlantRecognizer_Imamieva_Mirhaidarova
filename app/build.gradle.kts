plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.plantrecognizer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.plantrecognizer"
        minSdk = 24
        targetSdk = 34
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

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Обновлённые версии TensorFlow, без конфликтов
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    // Если нужна поддержка (FileUtil, ImageProcessor) — добавьте, но мы обойдёмся без неё
    // implementation("org.tensorflow:tensorflow-lite-support:0.5.0")
}