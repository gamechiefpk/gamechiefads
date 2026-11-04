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
    namespace = "com.gcl.gamechiefads.mediation"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

// No code: this module only pins the five mediation adapters we support, one tested set.
// Every adapter still depends on the legacy play-services-ads, which cannot live next to the Next-Gen SDK,
// so each one carries the exclude; apps should keep the global exclude from the README as well.
fun DependencyHandlerScope.adapter(coordinate: String) = api(coordinate) {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

dependencies {
    api(project(":gamechiefads-core"))
    adapter("com.google.ads.mediation:pangle:8.4.0.5.0")
    adapter("com.google.ads.mediation:unity:4.21.0.0")
    adapter("com.google.ads.mediation:facebook:6.22.0.1")
    adapter("com.google.ads.mediation:mintegral:17.1.91.0")
    adapter("com.google.ads.mediation:applovin:13.6.4.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libGroup
            artifactId = "gamechiefads-mediation"
            version = libVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("GamechiefAds Mediation")
                description.set("Pangle, Unity, Meta, Mintegral and AppLovin adapters for the Next-Gen GMA SDK")
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
