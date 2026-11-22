import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.enfoque.camara"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.enfoque.camara"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "2.0-creador"
    }

    // Firma fija: así cada APK nuevo se instala encima del anterior sin desinstalar.
    signingConfigs {
        create("fija") {
            storeFile = file("enfoque.keystore")
            storePassword = "enfoque123"
            keyAlias = "enfoque"
            keyPassword = "enfoque123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
        debug {
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=androidx.camera.camera2.interop.ExperimentalCamera2Interop",
                "-opt-in=androidx.camera.core.ExperimentalImageCaptureOutputFormat",
                "-opt-in=androidx.camera.core.ExperimentalZeroShutterLag",
                "-opt-in=androidx.camera.core.ExperimentalGetImage"
            )
        )
    }
}

dependencies {
    val camerax = "1.5.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("com.google.android.material:material:1.12.0")
}
