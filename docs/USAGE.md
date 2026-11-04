# GamechiefAds usage

Java and XML friendly. Everything fails open: any error means "no ad", never a crash or a stuck screen, and
every wait has a timeout. Debug builds always use Google's test ad units, whatever ids you configure.

## 1. Configure and init

In `Application.onCreate` (main thread):

```java
AdsConfig config = AdsConfig.builder()
    .appId("ca-app-pub-XXXX~YYYY")
    .interstitial(Placements.RANDOM).unitsRes(this, R.string.HF, R.string.MF, R.string.NORMAL)
        .everyNScreens(3).minGapSeconds(30)
        .excludeScreens(SplashActivity.class, IntroScreenActivity.class)
    .interstitial("after_save").units("ca-app-pub-.../...")
    .appOpen(Placements.SPLASH).unitsRes(this, R.string.OPEN_HF, R.string.OPEN_MF).tierTimeoutMs(3000)
    .appOpen(Placements.SPLASH_SECOND).units("...")            // optional second splash ad
    .appOpen(Placements.APP_OPEN).units("...").minGapSeconds(60)
        .excludeScreens(SplashActivity.class)
    .banner("banner_home").units("...")
    .nativeAd("native_list").units("...")
    .remoteConfigKey("ads_config")                              // optional, see section 9
    .build();
GamechiefAds.init(this, config);
GamechiefAds.setLoaderUi(new DefaultLoaderDialog());            // ui module; optional
```

Units are an ordered waterfall (high floor first). Each unit has its own timeout (`tierTimeoutMs`, default 3 s) and the
whole waterfall is capped by `overallBudgetMs` (default 8 s). Placement options: `units`, `unitsRes`, `mode`,
`everyNScreens`, `minGapSeconds`, `excludeScreens`, `tierTimeoutMs`, `enabled`. Global options: `appId`,
`remoteConfigKey`, `fallbackToLoad`, `autoRefill`, `overallBudgetMs`, `splashTimeoutMs`.

Placement names with a built-in meaning: `Placements.RANDOM`, `SPLASH`, `SPLASH_SECOND`, `APP_OPEN`.

## 2. Premium

```java
GamechiefAds.setPremium(isPremium);   // wire from your billing library's premium callback
```

Premium means no ads of any kind: nothing is requested, banners and natives hide, a load already in flight is dropped.

## 3. Splash

```java
GamechiefAds.prepareSplash(this, ads -> {          // runs once, inside config.splashTimeoutMs (default 10 s)
    showStartButton();
    startButton.setOnClickListener(v -> ads.show(this, () -> goNext()));   // goNext runs exactly once
});
```

Flow: consent (UMP) -> SDK init -> waterfall for `splash` -> done. Premium, consent denied, offline, no fill or
timeout all give `ads.hasAd() == false`; `ads.show` then calls your `onDone` straight away. If a `splash_second`
placement exists it loads in the background and is shown only after the first ad was closed.

## 4. Interstitial, on-demand (default)

```java
GamechiefAds.showInterstitial(activity, "after_save", new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { goNext(); }   // all methods are optional
});
GamechiefAds.showInterstitial(activity, "after_save", null);            // listener may be null
```

Shows the loader overlay, runs the waterfall, shows at most one ad. A second call while one is loading or showing is
refused (`onNotShown`). Exactly one of `onClosed` / `onNotShown` is called, then `onComplete(wasShown)`; navigate in
`onComplete`.

## 5. Random interstitial (no code in activities)

Configure the `Placements.RANDOM` placement. After a screen has resumed and settled (0.4 s), the library shows it when
all of these hold: at least `everyNScreens` screens since the last ad, at least `minGapSeconds` since the last ad of
any kind (including app-open), the screen is not excluded, the user is not premium, and no other ad is showing.
Rotation and returning from an ad do not count as screens.

## 6. Preloaded interstitial (opt-in)

```java
GamechiefAds.preloadInterstitial("after_save");                         // one request, none if one is ready/loading
GamechiefAds.isInterstitialReady("after_save");
boolean started = GamechiefAds.showPreloadedInterstitial(activity, "after_save", listener);
```

`showPreloadedInterstitial` returns false when nothing is ready (unless `fallbackToLoad(true)`, then it loads on demand).
After a preloaded show nothing is requested automatically unless you set `autoRefill(true)` and the placement `mode`
to `AdMode.PRELOAD`. A `PRELOAD` mode on the random placement shows what is ready, otherwise asks for one for next time.
Ready ads expire after 55 minutes.

## 7. App-open

Configure `Placements.APP_OPEN`. Shown when the app returns to the foreground if an ad is cached, the cooldown
(`minGapSeconds`) passed and the current screen is not in `excludeScreens` (put the splash there). The first ad is
fetched on the first normal screen. Before a purchase flow or a permission dialog:

```java
GamechiefAds.pauseAppOpenOnce();   // skips exactly the next return to foreground
```

## 8. Banner and native (ui module)

```xml
<com.gcl.gamechiefads.ui.BannerAdView
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:gads_placement="banner_home" />

<com.gcl.gamechiefads.ui.NativeAdTemplateView
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:gads_size="medium" app:gads_placement="native_list" />
```

Both show a shimmer while loading, hide themselves (GONE) when there is no ad (`gads_hideOnFail="false"` keeps the space),
and free the ad when they leave the window. Code: `view.load("native_list")`, `nativeView.setSize(NativeSize.SMALL)`.
Without the ui module: `GamechiefAds.banner(frameLayout, "banner_home")`.

Native sizes: `TINY` (one-line strip), `SMALL` (compact card), `SMALL_MEDIA` (video/image thumbnail left),
`MEDIUM`, `LARGE`, `FULL` (fills the parent, for dialogs and exit pages). Look:

```java
nativeView.setStyle(NativeStyle.builder()
    .background(Color.WHITE).headlineColor(Color.BLACK)
    .button(0xFF1A73E8, Color.WHITE).cornerRadiusDp(16).build());
```

or override the `gads_*` color names (`gads_card_bg`, `gads_cta_bg`, ...) in your app to retheme every view and both
light and dark. Your own native design: load with `GamechiefAds.loadNative(context, "placement", callback)` and bind the
`NativeAd` yourself (you must `destroy()` it).

## 9. Remote overrides (optional)

Read your Firebase Remote Config string yourself and pass it in (the library has no Firebase dependency):

```java
GamechiefAds.applyRemoteOverrides(remoteConfig.getString("ads_config"));
```

```json
{"enabled": true,
 "placements": {"random": {"units": ["a","b"], "every": 4, "gap": 45, "enabled": true,
                           "mode": "preload", "tierTimeoutMs": 3000}}}
```

Only existing placements can change; bad JSON or bad values are ignored. `"enabled": false` at the top switches all ads off.

## 10. Loader overlay

`GamechiefAds.setLoaderUi(new DefaultLoaderDialog())`, or your own XML: `new LayoutLoaderUi(R.layout.my_loader)`, or
implement `AdLoaderUi`. Without one, no overlay is shown.

## 11. Migrating an app

1. Remove `play-services-ads`; add the dependencies and the excludes from the README. Keep the `APPLICATION_ID` meta-data.
2. Delete `AppOpenManager`, the static ad flags and the per-activity `adCounter` calls. Use `Placements.RANDOM` and
   `Placements.APP_OPEN`.
3. Replace `showInterstitialAdWithLoader(activity, listener)` with `GamechiefAds.showInterstitial(activity, name, listener)`.
4. Replace the splash ad code with `prepareSplash`. Remove the `CONNECTIVITY_ACTION` receiver; the library does not
   need it (a missing network is just "no ad").
5. Third-party mediation adapters must be the Next-Gen compatible versions.

## 12. Behaviour guarantees

- Consent (UMP) runs before the SDK is initialised; a denied consent is not asked again for 60 s.
- Only one full-screen ad exists at a time (interstitial, app-open and splash share one flag; a 5 s watchdog frees the
  flag if the SDK never reports).
- Premium turned on while an ad is loading: the loaded ad is dropped, not shown.
- Ads are only shown on the visible, alive activity; if the user left, the ad is dropped.

## 13. Debug trace log

Debuggable builds print a trace to logcat, tag `GamechiefAds`, every line prefixed with its area. Release builds
print nothing (and build no log strings).

```
adb logcat -s GamechiefAds
```

Areas: `init`, `gate` (consent, SDK init), `consent`, `sdk` (load ok/failed with the AdMob error code),
`pipeline` and `waterfall` (each unit id, hit or miss, total time), `interstitial`, `random` (the policy decision with
screen count and gap), `appopen`, `splash`, `shower` (ad on screen, closed, watchdog), `pool`, `screen`, `state`
(premium), `banner`, `native`, `banner-ui`, `native-ui`, `loader`, `remote`.

To silence it in a debug build: `GamechiefAds.setDebugLogging(false)`.
