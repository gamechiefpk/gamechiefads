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
placement exists it loads in the background and is shown at the same time as the first (stacked, see section 20).

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

## 14. Mediation (AdMob mediation with network adapters)

Supported partners, one tested set in the `gamechiefads-mediation` module (versions from Google's Next-Gen adapter pages):

| Network | Adapter | Notes |
|---|---|---|
| Pangle | `pangle:8.4.0.5.0` | needs the bytedance maven repo. Call `PangleMediationAdapter.setPAConsent(...)` before init if you use consent. One ad source mapping per region (US, KR, ROW) in AdMob. |
| Unity Ads | `unity:4.21.0.0` | bidding only (waterfall support ended 31 Jan 2026). |
| Meta Audience Network | `facebook:6.22.0.1` | bidding only. |
| Mintegral | `mintegral:17.1.91.0` | needs the mintegral maven repo. Call `MBridgeSDKFactory.getMBridgeSDK().setDoNotTrackStatus(...)` after init if needed. |
| AppLovin | `applovin:13.6.4.2` | bidding and waterfall; banner only via waterfall. Disabled for child-directed / under-age apps. |

The single `gamechiefads` dependency already includes it; add the two vendor repositories from the README. Mediation itself needs no library code:
it is set up in the AdMob console and runs inside the ad unit ids you already pass in `AdsConfig`. The library gets the
order right: consent first, then SDK init, and ads are requested only after init completes (Google requires this so
bidding adapters take part in the first request).

What the app must do:

1. Keep the global excludes from the README. Every adapter still depends on the legacy `play-services-ads`, which cannot
   live next to the Next-Gen SDK (duplicate symbols). The module also excludes it per adapter, but other libraries may
   bring it in.
2. Add every mediation partner to the AdMob Privacy & messaging partner lists (GDPR / US states), or the partner will
   not serve.
3. Banners: turn off auto refresh in each third-party network's own ad unit settings.
4. Unity, Meta and Pangle US need an Activity context for some formats: always load through the library (it uses the
   current Activity) rather than calling the SDK yourself.

Check which adapters are really active in a debug build, `adb logcat -s GamechiefAds`:

```
[mediation] AdMobAdapter: READY (310ms); FacebookAdapter: READY (1200ms)
[sdk] load ok: InterstitialAd via Meta Audience Network (640ms) [tried: AdMob Network:err 210ms, Meta Audience Network:ok 640ms]
```

The first line is the adapter status after init, the second shows who served each ad and which sources failed first.
An adapter missing from the first line was not initialised (wrong version, missing exclude, or not in the AdMob group).
Not verified against real adapters yet: the emulator test used Google's test ids only.

## 15. Placements as constants

Declare every ad spot once, in upper case, and pass the constant instead of a string:

```java
public final class AdIds {
    public static final AdPlacement AFTER_SAVE  = AdPlacement.of("after_save");
    public static final AdPlacement BANNER_HOME = AdPlacement.of("banner_home");
    public static final AdPlacement EXIT_NATIVE = AdPlacement.of("exit_native");
}
// config:  .interstitial(AdIds.AFTER_SAVE).units(...)
// use:     GamechiefAds.showInterstitial(this, AdIds.AFTER_SAVE, null);
//          AdsUi.banner(frameLayout, AdIds.BANNER_HOME);
```

The string methods still work. Built-in constants: `AdPlacement.RANDOM`, `APP_OPEN`, `SPLASH`, `SPLASH_SECOND`.

## 16. Banner types

The type is part of the placement, not of the call:

```java
.banner(AdIds.BANNER_HOME).units("...").bannerType(BannerType.ADAPTIVE)             // default
.banner(AdIds.BANNER_BOTTOM).units("...").bannerType(BannerType.COLLAPSIBLE_BOTTOM) // expands upwards
.banner(AdIds.BANNER_RECT).units("...").bannerType(BannerType.MEDIUM_RECTANGLE)     // 300x250
```

`ADAPTIVE`, `INLINE_ADAPTIVE`, `COLLAPSIBLE_BOTTOM`, `COLLAPSIBLE_TOP`, `BANNER` (320x50), `LARGE_BANNER` (320x100),
`MEDIUM_RECTANGLE`, `FULL_BANNER` (468x60), `LEADERBOARD` (728x90). Remote Config can change it: `"bannerType": "MEDIUM_RECTANGLE"`.
Collapsible banners belong at the edge of the screen (bottom banner: `COLLAPSIBLE_BOTTOM`) and are served by Google demand
only; a mediated network shows a normal banner. Debug builds log whether the SDK granted it (`collapsible=true`).

Loading needs no XML, only the FrameLayout the app already has (ui module):

```java
AdsUi.banner(findViewById(R.id.bannerContainer), AdIds.BANNER_HOME);   // shimmer, hides itself without an ad
```

Without the ui module: `GamechiefAds.banner(frameLayout, AdIds.BANNER_HOME)`.

## 17. Full-screen native ad (like an interstitial)

```java
FullScreenNativeAd.show(this, AdIds.EXIT_NATIVE, new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { finish(); }
});
// optional: new FullScreenNativeAd.Options(3 /* close after s */, NativeStyle.DEFAULT, DefaultLoaderDialog.builder().build())
```

A `nativeAd(...)` placement shown over the whole screen with a close button that works after a short countdown (back is
blocked until then). It takes the single full-screen slot: premium users never see it, no other ad can start while it is
open, and it resets the gap and screen counter. For your own full-screen design use
`GamechiefAds.openFullScreenSlot(activity, placement)` and call `release()` when your screen closes.

## 18. Loading overlay

```java
GamechiefAds.setLoaderUi(DefaultLoaderDialog.builder()
    .title("Designing your room").message("A short ad keeps this app free")
    .tips("Tip: swipe to compare", "Tip: try night mode")      // optional, fade through
    .accentColor(0xFF6C4CF1).cornerRadiusDp(28).build());
```

Dimmed and blurred background (Android 12+), rounded card, spinner, texts that follow light and dark mode. Texts per
placement win over the global ones: `.interstitial(AdIds.AFTER_SAVE).loaderText("Saving...", "One short ad")` or Remote
Config `loaderTitle` / `loaderMessage`.

## 19. No ads on back presses

The random interstitial counts and triggers only when a NEW screen opens (a freshly created Activity). Coming back to an
earlier screen with the back button, or because the screen above finished, is not a screen: no count, no ad. Rotation and
returning from an ad were never counted either. App code that calls `showInterstitial` itself decides for itself, for
example not inside `onBackPressed`.

## 20. Double ads (two ads, one ad break)

```java
.interstitial(AdIds.AFTER_SAVE).units(...).doubleAd(true)
.appOpen(Placements.APP_OPEN).units(...).doubleAd(true)
```

Both ads are requested at the same time and, once loaded (the second is waited for at most `doubleSecondWaitMs`, default
2.5 s; if it is missing the first is shown alone), BOTH are shown at the same moment and stack on screen. The user closes
the top ad and the other one is already underneath, so it looks as if the first ad never closed: no gap, no flash of the
app, no second ad launch. The "ad showing" flag is held until the last one is closed. Your listener / `onDone` is called
once, after the last ad. Remote Config switches it per placement:
`{"placements":{"after_save":{"double":true},"app_open":{"double":true},"random":{"double":true}}}`. This includes the automatic random interstitial (`.interstitial(Placements.RANDOM)...doubleAd(true)`): when the screen rule fires, two ads are loaded together and shown stacked.

Splash: set `.doubleAd(true)` on the splash placement (or enable `splash_second`) and both splash ads are shown together
the same way. Preloaded interstitials stay single.

## 21. Floors (the ad unit waterfall) under your control

`units(HF, MF, NORMAL)` is tried top to bottom. Per placement you can change how:

```java
.interstitial(AdIds.AFTER_SAVE).units(HF, MF, NORMAL)
    .tierTimeoutMs(3000)                 // default timeout of a unit
    .unitTimeoutMs(HF, 1500)             // the high floor rarely fills: give it less time
    .skipUnitAfterFails(3, 120)          // 3 misses in a row: skip that unit for 120 s, then try it again
    .disableUnit(MF)                     // keep the id, stop requesting it
    .waterfallMode(WaterfallMode.PARALLEL)   // all units at once, first ad wins (more requests, less waiting)
```

Global: `overallBudgetMs` caps the whole waterfall. A placement never ends up with zero units (if every unit is skipped, all
are tried). Everything is also a Remote Config field:
`{"placements":{"after_save":{"waterfall":"parallel","tierTimeouts":{"ca-app-pub-...":1500},"disabledUnits":["..."],"skipAfterFails":3,"skipForSec":120}}}`.

Debug builds replace your ids with Google test ids, so floors cannot be seen in debug unless you call
`.testIdsInDebug(false)` on the config (release builds always use your ids). The trace shows every unit:
`[pipeline] after_save: SEQUENTIAL waterfall, 2/3 unit(s)` (a unit was skipped) and `[waterfall] tier ...: miss`.

## 22. Splash options: app open or interstitial, flooring, double, splash banner

The splash ad is an **app-open ad by default**. Each option is a placement setting, so it also works from Remote Config.

```java
// default: app open, with floors, one ad
.appOpen(Placements.SPLASH).units(HF, MF, NORMAL)

// an interstitial instead of an app-open ad (use interstitial unit ids): same logic, same options
.interstitial(Placements.SPLASH).units(HF, MF, NORMAL)

// flooring off: ONE request, to the LAST unit (the lowest floor, the one that normally always fills)
.appOpen(Placements.SPLASH).units(HF, MF, NORMAL).flooring(false)

// double: two ads requested together, shown at the same time (stacked, so closing one reveals the other), onDone runs once after the last
.appOpen(Placements.SPLASH).units(HF, MF, NORMAL).doubleAd(true)
```

Every combination works (app open or interstitial, flooring on or off, double on or off). The splash code does not change:
`prepareSplash` then `ads.show(this, onDone)`. The older `splash_second` placement still works if you want the second ad
to use different units.

Remote Config (units must match the format, so send them with `type`):

```json
{"placements":{"splash":{"type":"interstitial","units":["ca-app-pub-.../inter_hf","ca-app-pub-.../inter_normal"],"flooring":false,"double":true}}}
```

`flooring` works on every placement, not only the splash.

**Splash banner.** A `LARGE_BANNER` (320x100) for the splash screen, loaded while the splash ad loads:

```java
// config:  .banner(AdPlacement.SPLASH_BANNER).units(...)
GamechiefAds.bannerForSplash(findViewById(R.id.splashBanner));   // core: a FrameLayout, you add nothing
AdsUi.bannerForSplash(findViewById(R.id.splashBanner));          // ui module: same, plus a shimmer while it loads
```

The size is fixed to LARGE_BANNER whatever the placement says. Premium, flooring, units, timeouts and Remote Config work as
for any banner; the container is hidden when there is no ad, and the banner is destroyed when the screen closes.

The sample app has a **Splash lab** screen to try every combination.
