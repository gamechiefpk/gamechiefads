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
    namespace = "com.gcl.gamechiefads.all"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

// No code: the one-line dependency. Pulls core + ui + mediation (Pangle, Unity, Meta, Mintegral, AppLovin).
dependencies {
    api(project(":gamechiefads-core"))
    api(project(":gamechiefads-ui"))
    api(project(":gamechiefads-mediation"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libGroup
            artifactId = "gamechiefads"
            version = libVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("GamechiefAds (all-in-one)")
                description.set("Core, UI and mediation adapters in one dependency")
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
