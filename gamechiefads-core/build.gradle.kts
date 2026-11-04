plugins {
    id("com.android.library")
    id("maven-publish")
}

val libGroup = providers.gradleProperty("LIB_GROUP").get()
val libVersion = (System.getenv("RELEASE_VERSION") ?: providers.gradleProperty("LIB_VERSION").get())
    .removePrefix("v")
val gprRepo = System.getenv("GITHUB_REPOSITORY") ?: providers.gradleProperty("GPR_REPO").get()

android {
    namespace = "com.gcl.gamechiefads"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

// Kotlin interface default methods become real Java default methods (optional listener callbacks)
kotlin { compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") } }

dependencies {
    // api: the ui module (and apps) bind NativeAd/AdView types returned by the sdk/ sources
    api("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // Real org.json for JVM unit tests (android.jar only has stubs)
    testImplementation("org.json:json:20260814")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libGroup
            artifactId = "gamechiefads-core"
            version = libVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("GamechiefAds Core")
                description.set("Thin shared ads library on the Next-Gen GMA SDK")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/$gprRepo")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
