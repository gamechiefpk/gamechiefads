plugins {
    id("com.android.application")
}

android {
    namespace = "com.gcl.gamechiefads.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gcl.gamechiefads.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // In your own apps use: implementation("com.gcl:gamechiefads-core:0.1.0")
    implementation(project(":gamechiefads-core"))
    implementation(project(":gamechiefads-ui"))
    implementation("androidx.appcompat:appcompat:1.8.0")
}
