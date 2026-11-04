# GamechiefAds

Shared ads library for our Android apps, built on Google's Next-Gen GMA SDK.

## Setup

`settings.gradle` (GitHub Packages, use a token with `read:packages`):

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/ah-gamechief/gamechiefads")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
```

App `build.gradle`:

```groovy
dependencies {
    implementation "com.gcl:gamechiefads-core:0.1.0"
    implementation "com.gcl:gamechiefads-ui:0.1.0"   // optional: native/banner views, loader dialog
}

configurations.all {   // mediation adapters still pull in the old SDK; the two cannot coexist
    exclude group: "com.google.android.gms", module: "play-services-ads"
    exclude group: "com.google.android.gms", module: "play-services-ads-lite"
}
```

`AndroidManifest.xml` (inside `<application>`):

```xml
<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-XXXX~YYYY" />
```

Remove `com.google.android.gms:play-services-ads` from your app. Then see [docs/USAGE.md](docs/USAGE.md).
