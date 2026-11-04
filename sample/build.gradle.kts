plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gcl.gamechiefads.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gcl.gamechiefads.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
}

dependencies {
    // In your own apps use: implementation("com.gcl:gamechiefads-core:0.1.0")
    implementation(project(":gamechiefads-core"))
    implementation(project(":gamechiefads-ui"))
    implementation("androidx.appcompat:appcompat:1.7.0")
}
