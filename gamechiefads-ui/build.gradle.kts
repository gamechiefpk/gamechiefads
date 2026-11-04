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
    namespace = "com.gcl.gamechiefads.ui"
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

// Kotlin interface default methods become real Java default methods (optional listener callbacks)
kotlin { compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") } }

dependencies {
    api(project(":gamechiefads-core"))
    implementation("androidx.annotation:annotation:1.11.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libGroup
            artifactId = "gamechiefads-ui"
            version = libVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("GamechiefAds UI")
                description.set("Optional views for GamechiefAds (shimmer, banner, native, loader)")
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
