plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "com.sentinel.guardian"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sentinel.guardian"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation("io.coil-kt.coil3:coil-compose:3.2.0")

    implementation("com.google.crypto.tink:tink-android:1.18.0")

    implementation("androidx.biometric:biometric-ktx:1.4.0-alpha02")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation(libs.androidx.appcompat)

    // CameraX core and lifecycle libraries
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion") // Camera2 implementation
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    // For Video Capture
    implementation("androidx.camera:camera-video:$cameraxVersion")

    // For View extensions, although we won't use a PreviewView, it's good to have
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:${cameraxVersion}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5") // Check for the latest version

    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.1") // Import Compose Navigation

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation(project(":models"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}