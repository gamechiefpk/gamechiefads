# GamechiefAds: how do I...?

Copy-paste recipes. Each one says what you want, then shows the code. Full option list: [USAGE.md](USAGE.md).

Good to know:
- Debug builds use Google test ads and print a trace: `adb logcat -s GamechiefAds`.
- Every call is safe. If something goes wrong you get no ad, never a crash or a stuck screen.
- Premium users never see anything (see 5).

---

## Setup

### 1. Set it up once (in `Application.onCreate`)

```java
GamechiefAds.init(this, AdsConfig.builder()
    .appId("ca-app-pub-XXXX~YYYY")

    .appOpen(Placements.SPLASH).units(HF, MF, NORMAL)            // splash ad
    .banner(AdPlacement.SPLASH_BANNER).units(BANNER_UNIT)        // splash banner
    .interstitial(Placements.RANDOM).units(HF, MF, NORMAL)       // automatic, between screens
        .everyNScreens(3).minGapSeconds(30).excludeScreens(SplashActivity.class)
    .interstitial(AdIds.AFTER_SAVE).units(HF, MF, NORMAL)        // your own spot, called by name
    .appOpen(Placements.APP_OPEN).units(...).minGapSeconds(60)   // when the user returns to the app
        .excludeScreens(SplashActivity.class)
    .banner(AdIds.BANNER_HOME).units(BANNER_UNIT)
    .nativeAd(AdIds.NATIVE_LIST).units(NATIVE_UNIT)
    .build());
```

`units(HF, MF, NORMAL)` means: try the high floor first, then the next one (see 12).
Name your spots once in a class and use the constants everywhere:

```java
final class AdIds {
    static final AdPlacement AFTER_SAVE = AdPlacement.of("after_save");
    static final AdPlacement BANNER_HOME = AdPlacement.of("banner_home");
}
```

### 2. Premium users see no ads

```java
GamechiefAds.setPremium(isPremium);   // call it from your billing library's callback
```

---

## Splash

### 3. Show a splash ad, then open the app

```java
GamechiefAds.prepareSplash(this, ads -> {
    startButton.setVisibility(View.VISIBLE);
    startButton.setOnClickListener(v ->
        ads.show(this, () -> startActivity(new Intent(this, MainActivity.class))));
});
```

`onDone` runs exactly once, with or without an ad (offline, premium, no fill). Always open your next screen in it.

### 4. Splash options (all in the config, all can be changed from Remote Config)

| I want | Config |
|---|---|
| App open ad (default) | `.appOpen(Placements.SPLASH).units(...)` |
| Interstitial instead | `.interstitial(Placements.SPLASH).units(...)` |
| Use the floors (default) | `.flooring(true)` |
| One request only, no floors | `.flooring(false)` (asks the LAST unit in your list) |
| Two ads in a row | `.doubleAd(true)` |

Any mix works, for example an interstitial with no floors and a double:

```java
.interstitial(Placements.SPLASH).units(HF, MF, NORMAL).flooring(false).doubleAd(true)
```

### 5. A large banner on the splash screen

```java
// config: .banner(AdPlacement.SPLASH_BANNER).units(BANNER_UNIT)
AdsUi.bannerForSplash(findViewById(R.id.splashBanner));   // any FrameLayout, always a 320x100 large banner
```

No XML view needed, just give it a `FrameLayout`.

---

## Interstitials

### 6. An ad after an action (save, export, level done)

```java
GamechiefAds.showInterstitial(this, AdIds.AFTER_SAVE, new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { openResultScreen(); }
});
```

Continue in `onComplete`: it runs whether or not an ad was shown. A "Ad is loading, please wait" box shows while it loads.
You can pass `null` instead of the listener.

### 7. An ad every few screens with no code in your activities

Just configure `Placements.RANDOM` (see 1). The library counts new screens itself. It never shows on a screen you
excluded, on a back press, while another ad is open, or to premium users.

### 8. Two ads in a row, as if it was one ad (double)

```java
.interstitial(AdIds.AFTER_SAVE).units(...).doubleAd(true)       // in the config
// or from Remote Config, per spot, no app update:
// {"placements":{"after_save":{"double":true},"random":{"double":true},"splash":{"double":true}}}
```

Both ads are loaded together and shown at the same time, one on top of the other. When the user closes the first, the
second is already there, so it feels like the same ad. Works for after-action ads, the random ad, the splash and app open.

### 9. Change the "Ad is loading" text

```java
GamechiefAds.setLoaderText("Ad is loading", "Please wait...");   // for all spots, any time (e.g. after a language change)
// config, all spots:       .defaultLoaderText("Ad is loading", "Please wait...")
// config, one spot:        .interstitial(AdIds.AFTER_SAVE).units(...).loaderText("Saving", "One short ad first")
// Remote Config, all:      {"loaderTitle":"Ad is loading","loaderMessage":"Please wait..."}
```

A spot's own text wins over the global text. Looks (colors, tips): `GamechiefAds.setLoaderUi(DefaultLoaderDialog.builder()...build())`.

### 10. Load an ad early for a time-critical moment (optional)

```java
GamechiefAds.preloadInterstitial(AdIds.LEVEL_END);          // e.g. when the level starts
boolean started = GamechiefAds.showPreloadedInterstitial(this, AdIds.LEVEL_END, listener);
if (!started) nextLevel();                                   // nothing was ready: carry on
```

Normal ads (6) are loaded on demand. Use preload only when waiting would hurt.

---

## Other formats

### 11. App open ad when the user returns to the app

Configure `Placements.APP_OPEN` (see 1). Before something that leaves your app on purpose (billing, camera, permissions):

```java
GamechiefAds.pauseAppOpenOnce();
```

### 12. Banner

No XML: give the library a `FrameLayout`.

```java
// config: .banner(AdIds.BANNER_HOME).units(...).bannerType(BannerType.MEDIUM_RECTANGLE)
AdsUi.banner(findViewById(R.id.bannerContainer), AdIds.BANNER_HOME);
```

Types: `ADAPTIVE` (default), `COLLAPSIBLE_BOTTOM`, `COLLAPSIBLE_TOP`, `MEDIUM_RECTANGLE`, `LARGE_BANNER`, `BANNER`,
`INLINE_ADAPTIVE`, `FULL_BANNER`, `LEADERBOARD`. The container hides itself if there is no ad.

### 13. Native ad in a list or card

```xml
<com.gcl.gamechiefads.ui.NativeAdTemplateView
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:gads_size="medium" app:gads_placement="native_list" />
```

Sizes: `tiny`, `small`, `small_media`, `medium`, `large`, `full`.

### 14. A full-screen native ad when leaving a screen

```java
@Override public void onBackPressed() {
    FullScreenNativeAd.show(this, AdIds.EXIT_NATIVE, new InterstitialListener() {
        @Override public void onComplete(boolean wasShown) { finish(); }
    });
}
```

---

## Control and fixing

### 15. The high floor keeps missing (floors)

```java
.interstitial(AdIds.AFTER_SAVE).units(HF, MF, NORMAL)
    .unitTimeoutMs(HF, 1500)          // give the high floor less time
    .skipUnitAfterFails(3, 120)       // 3 misses in a row: skip it for 2 minutes
    .waterfallMode(WaterfallMode.PARALLEL)   // optional: ask all units at once
```

Or switch floors off: `.flooring(false)` (one request, to the last unit).

### 16. Change rules without an app update (Remote Config)

```java
GamechiefAds.applyRemoteOverrides(remoteConfig.getString("ads_config"));
```

```json
{"placements": {"random": {"every": 5, "gap": 60, "double": true},
                "banner_home": {"enabled": false},
                "splash": {"flooring": false}}}
```

You read the string, the library applies it. Bad JSON is ignored. `{"enabled": false}` turns every ad off.

### 17. Mediation (Pangle, Unity, Meta, Mintegral, AppLovin)

Nothing to code: add the partners to your ad units in the AdMob console and use those unit ids.
Setup lines are in the README. In a debug build the trace shows which network answered.

### 18. Why did no ad show?

```bash
adb logcat -s GamechiefAds
```

Look at `[random]` (the reason: `DENY_MIN_GAP`, `DENY_EVERY_N`, `DENY_EXCLUDED_SCREEN`, `DENY_PREMIUM`), `[gate]` (consent or
init) and `[pipeline]` / `[sdk]` (the AdMob error of each unit). Release builds print nothing.
