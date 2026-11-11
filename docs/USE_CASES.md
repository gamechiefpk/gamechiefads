# GamechiefAds: use cases

Copy-paste recipes in Java and XML. For all options see [USAGE.md](USAGE.md). Debug builds always use Google's test ad
units and print a trace to logcat (`adb logcat -s GamechiefAds`).

## 1. Set it up once

`Application.onCreate`:

```java
GamechiefAds.init(this, AdsConfig.builder()
    .appId("ca-app-pub-XXXX~YYYY")
    .interstitial(Placements.RANDOM).unitsRes(this, R.string.HF, R.string.MF, R.string.NORMAL)
        .everyNScreens(3).minGapSeconds(30)
        .excludeScreens(SplashActivity.class)
    .interstitial("after_save").units("ca-app-pub-.../...")
    .appOpen(Placements.SPLASH).units("ca-app-pub-.../...")
    .appOpen(Placements.APP_OPEN).units("ca-app-pub-.../...").minGapSeconds(60)
        .excludeScreens(SplashActivity.class)
    .banner("banner_home").units("ca-app-pub-.../...")
    .nativeAd("native_list").units("ca-app-pub-.../...")
    .build());
GamechiefAds.setLoaderUi(new DefaultLoaderDialog());
```

Several units for one placement are a waterfall (high floor first). Add more `.interstitial(name)` blocks for more spots.

## 2. Splash screen with an ad, then go to the app

```java
GamechiefAds.prepareSplash(this, ads -> {
    startButton.setVisibility(View.VISIBLE);
    startButton.setOnClickListener(v ->
        ads.show(this, () -> startActivity(new Intent(this, MainActivity.class))));
});
```

The callback always arrives (consent denied, offline, premium or no fill give "no ad") and `onDone` runs exactly once,
so navigation can never run twice. Add a `splash_second` app-open placement for a second ad; it shows only after the
first one was closed.

## 3. An interstitial after an action (save, export, level done)

```java
GamechiefAds.showInterstitial(this, "after_save", new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { openResultScreen(); }
});
```

Always continue in `onComplete`: it runs whether or not an ad was shown. The loader overlay appears while the ad loads.
Fire-and-forget also works: `GamechiefAds.showInterstitial(this, "after_save", null);`

## 4. Interstitials between screens with no code in activities

Only configure the `Placements.RANDOM` placement (see 1). The library counts screens itself and shows the ad when
`everyNScreens` screens and `minGapSeconds` have passed, never on an excluded screen, never for premium users, and
never while another ad is showing. Rotation and coming back from an ad do not count as screens.

## 5. Premium users see no ads

```java
// from your billing library's premium callback
GamechiefAds.setPremium(isPremium);
```

Nothing is requested, banners and natives hide, and an ad that is still loading is dropped.

## 6. Preload an interstitial for a time-critical moment

```java
GamechiefAds.preloadInterstitial("level_end");              // e.g. when the level starts

// later
boolean started = GamechiefAds.showPreloadedInterstitial(this, "level_end", new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { nextLevel(); }
});
if (!started) nextLevel();                                  // nothing was ready: carry on
```

Preloading is opt-in and never refills by itself. `GamechiefAds.isInterstitialReady("level_end")` tells you if one is
ready. Prefer the on-demand flow (use case 3) unless the wait would hurt.

## 7. App-open ad when returning to the app

Configure `Placements.APP_OPEN` (see 1). Before something that leaves the app on purpose (a purchase sheet, a
permission dialog, the camera), skip exactly one return:

```java
GamechiefAds.pauseAppOpenOnce();
billingClient.launchBillingFlow(activity, params);
```

## 8. Banner in a layout

```xml
<com.gcl.gamechiefads.ui.BannerAdView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:gads_placement="banner_home" />
```

A shimmer shows while it loads, it disappears if there is no ad (no empty gap), and it frees the banner when the screen
closes.

## 9. Native ad in a list or a card, any size

```xml
<com.gcl.gamechiefads.ui.NativeAdTemplateView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:gads_size="medium"
    app:gads_placement="native_list" />
```

Sizes: `tiny`, `small`, `small_media`, `medium`, `large`, `full` (fills the parent: dialogs, exit pages).
Restyle in code:

```java
nativeView.setStyle(NativeStyle.builder()
    .background(Color.WHITE).headlineColor(Color.BLACK)
    .button(0xFF1A73E8, Color.WHITE).cornerRadiusDp(16).build());
```

## 10. Change ad rules without an app update (Remote Config)

```java
GamechiefAds.applyRemoteOverrides(remoteConfig.getString("ads_config"));
```

```json
{"placements": {"random": {"every": 5, "gap": 60}, "banner_home": {"enabled": false}}}
```

The library has no Firebase dependency: you read the string, it applies it. Bad JSON is ignored. Switch everything off
with `{"enabled": false}`.

## 11. Mediation (Pangle, Unity, Meta, Mintegral, AppLovin)

Nothing to code: add the mediation partners to your ad units in the AdMob console and use those unit ids. Setup lines
are in the README and the partner table is in [USAGE.md](USAGE.md#14-mediation-admob-mediation-with-network-adapters).
Check in a debug build which network answered:

```
[mediation] AdMobAdapter: READY (310ms); FacebookAdapter: READY (1200ms)
[sdk] load ok: InterstitialAd via Meta Audience Network (640ms) [tried: AdMob Network:err 210ms, ...]
```

## 12. Find out why no ad appeared

```bash
adb logcat -s GamechiefAds
```

Look at the `[random]` line for the policy decision (`DENY_MIN_GAP`, `DENY_EVERY_N`, `DENY_EXCLUDED_SCREEN`,
`DENY_PREMIUM`), at `[gate]` for consent or init problems, and at `[pipeline]` / `[sdk]` for the AdMob error code of
each unit. Release builds print nothing.

## 13. Banner types and a code-only banner

```java
// config: .banner(AdIds.BANNER_RECT).units("...").bannerType(BannerType.MEDIUM_RECTANGLE)
AdsUi.banner(findViewById(R.id.bannerContainer), AdIds.BANNER_RECT);   // a FrameLayout, no XML view needed
```

## 14. A full-screen native ad when leaving a screen

```java
@Override public void onBackPressed() {
    FullScreenNativeAd.show(this, AdIds.EXIT_NATIVE, new InterstitialListener() {
        @Override public void onComplete(boolean wasShown) { finish(); }
    });
}
```

## 15. Two ads in a row, switched from Remote Config

```java
.interstitial(AdIds.AFTER_SAVE).units(...)            // normal
// Remote Config: {"placements":{"after_save":{"double":true}}}   -> two ads back to back, one break
```

## 16. A high floor that keeps missing

```java
.interstitial(AdIds.AFTER_SAVE).units(HF, MF, NORMAL).unitTimeoutMs(HF, 1500).skipUnitAfterFails(3, 120)
```
