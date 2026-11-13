plugins {
    id("com.android.library")
    id("maven-publish")
}

val libVersion = (System.getenv("RELEASE_VERSION") ?: providers.gradleProperty("LIB_VERSION").get())
    .removePrefix("v")
val gprRepo = System.getenv("GITHUB_REPOSITORY") ?: providers.gradleProperty("GPR_REPO").get()
// JitPack builds publish as com.github.<owner>.<repo>:<module>:<tag>; everything else uses LIB_GROUP
val libGroup = if (System.getenv("JITPACK") != null) "com.github.${gprRepo.replace('/', '.')}" else providers.gradleProperty("LIB_GROUP").get()

android {
    namespace = "com.gcl.gamechiefads.remoteconfig"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

kotlin { compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") } }

// Firebase Remote Config for the core module (found by name). The app still needs the usual Firebase setup:
// google-services.json and the com.google.gms.google-services plugin (see the README).
dependencies {
    api(project(":gamechiefads-core"))
    api(platform("com.google.firebase:firebase-bom:35.2.0"))
    api("com.google.firebase:firebase-config")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libGroup
            artifactId = "gamechiefads-remoteconfig"
            version = libVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("GamechiefAds Remote Config")
                description.set("Firebase Remote Config for GamechiefAds: ad rules and your own values")
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
