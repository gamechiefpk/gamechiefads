# GamechiefAds

Shared ads library for our Android apps, built on Google's Next-Gen GMA SDK.

## Setup

`settings.gradle` (JitPack, no token needed):

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

App `build.gradle` (replace `TAG` with the latest release tag, e.g. `v0.1.0`):

```groovy
dependencies {
    implementation "com.github.gamechiefpk.gamechiefads:gamechiefads-core:TAG"
    implementation "com.github.gamechiefpk.gamechiefads:gamechiefads-ui:TAG"          // optional: native/banner views, loader dialog
    implementation "com.github.gamechiefpk.gamechiefads:gamechiefads-mediation:TAG"   // optional: Pangle, Unity, Meta, Mintegral, AppLovin adapters
}

configurations.all {   // mediation adapters still pull in the old SDK; the two cannot coexist
    exclude group: "com.google.android.gms", module: "play-services-ads"
    exclude group: "com.google.android.gms", module: "play-services-ads-lite"
}
```

Only if you use the mediation module, also add these two repositories next to `jitpack.io`:

```groovy
maven { url = uri("https://artifact.bytedance.com/repository/pangle/") }
maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
```

`AndroidManifest.xml` (inside `<application>`):

```xml
<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-XXXX~YYYY" />
```

Remove `com.google.android.gms:play-services-ads` from your app. Then see [docs/USAGE.md](docs/USAGE.md).
