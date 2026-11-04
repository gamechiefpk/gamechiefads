pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // vendor repositories needed by the mediation adapters (apps need these two lines too)
        maven("https://artifact.bytedance.com/repository/pangle/")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
    }
}
rootProject.name = "gamechiefads"
include(":gamechiefads-core")
include(":gamechiefads-ui")
include(":gamechiefads-mediation")
include(":gamechiefads-all")
include(":sample")
