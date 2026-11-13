# GamechiefAds: build brief for Claude Code

Goal: a thin, reliable, shared **ads library for Android apps**, built on Google's **Next-Gen GMA SDK**, shared across the owner's apps
(Java Activities, XML UI). Owner builds non-game Android apps, avoids paid SDKs, wants simple setup for teammates.
Sibling project (same publishing approach): `E:\Claude\playsubs-project` (PlaySubs, subscriptions library, github.com/ah-gamechief/playsubs).
Nothing here has been compiled yet. **You have the Android SDK and Gradle: compile and test everything.**

## Decisions already made with the owner
- Name: public facade class **`GamechiefAds`** (renamed from `Ads`). Package `com.gcl.gamechiefads`. Artifacts: `com.gcl:gamechiefads-core`, optional `com.gcl:gamechiefads-ui`.
- **Kotlin internals** (coroutines, StateFlow), **Java-friendly public API** (`@JvmStatic`, plain callback interfaces, builders). No Compose in the library.
- **Next-Gen SDK only** (no support for the old play-services-ads).
- **Ultra thin: many small single-purpose classes, NOT one big class.** Dependency direction is one-way:
  facade -> controllers -> policy/pool/waterfall -> sdk sources. Only the `sdk/` package imports Next-Gen classes.
- **Fail open**: any error at the SDK boundary = no ad, never a crash or stuck screen. Never block the user (everything has a timeout).
- **Do not replace the existing on-demand interstitial flow with preloading.** Keep on-demand (loader dialog, waterfall, load then show) as the DEFAULT.
  Add preloading as extra, opt-in methods.
- No dependency on PlaySubs: app wires premium with `GamechiefAds.setPremium(boolean)` from PlaySubs' `onPremiumChanged`.
- Code-based shimmer (no Lottie / shimmer library) in the ui module.
- README for teammates: only how to add the dependency (setup lines). Longer guide goes to `docs/USAGE.md`.

## Structure
```
gamechiefads-core (no UI)
  GamechiefAds.kt          facade, delegates only
  AdsConfig.kt             placements + rules as plain data, Java-friendly builder
  RemoteOverrides.kt       optional JSON (Firebase Remote Config string) overrides config: unit ids, every, gap, enabled, mode per placement
  AdState.kt               only shared state: premium, adShowing, lastShownAt, screen counter (StateFlow)
  AdPolicy.kt              PURE logic "may I show now?" (premium, min gap, every-N, excluded screens, ad already showing). No Android imports. Unit-test it.
  Waterfall.kt             generic: try ordered unit ids, per-tier timeout, returns first success or null
  AdPool.kt                one ready ad per placement for the PRELOAD path only; expiry, refill
  Consent.kt               UMP wrapper (suspend)
  SdkInit.kt               one-time Next-Gen init off the main thread
  ScreenTracker.kt         ActivityLifecycleCallbacks: current activity (weak), screen count, resumed events, excluded screens
  InterstitialController.kt  two paths: ON_DEMAND (default) and PRELOADED
  AppOpenController.kt     process lifecycle app-open (replaces AppOpenManager), cooldown, skip list, "pause once" for purchase/other flows
  SplashFlow.kt            consent -> init -> waterfall -> timeout, then second (double) app-open only AFTER the first closes
  sdk/ InterstitialSource, AppOpenSource, NativeSource, BannerSource   (callbacks -> suspend, runCatching boundary)
gamechiefads-ui (optional)
  ShimmerView (custom View, gradient + ValueAnimator, starts on attach, cancels on detach), banner/native views, LoaderDialog behind a small interface (default dialog, replaceable by app XML)
sample/  app with Google TEST ad unit ids (debug builds must use test ids automatically)
docs/USAGE.md, README.md (setup lines only)
```

## Public API (the entire surface; keep it this small and clearly named: every method is `load<What>Ad`)
All ad units are given once to `GamechiefAds.init(app, config)` through slot methods (`splashAppOpenUnits(...)`, `bannerUnits(...)`,
`randomInterstitialUnits(...)`, `randomInterstitialDoubleUnits(...)`, `exitBannerUnits(...)`, `appOpenUnit(id)`, `introNativeUnits(...)`,
`fullScreenNativeUnit(id)`, `rewardedUnits(...)`...). Several ids = floors (first id first). `appOpenUnit` and `fullScreenNativeUnit`
take ONE unit, no floors. Second ads of a double have their own units.
```java
GamechiefAds.init(app, config);   GamechiefAds.setPremium(boolean);   GamechiefAds.applyRemoteConfig(json);
GamechiefAds.setLoadingDialog(ui); GamechiefAds.setLoadingText(title, message); GamechiefAds.skipNextAppOpenAd();
GamechiefAds.loadSplashAppOpenAd(activity, callback);         // + loadSplashAppOpenDoubleAd, loadSplashInterstitialAd, loadSplashInterstitialDoubleAd
GamechiefAds.loadSplashBannerAd(frameLayout);                 // always LARGE_BANNER
GamechiefAds.loadBannerAd(frameLayout);  GamechiefAds.loadExitBannerAd(frameLayout);
GamechiefAds.loadCustomBannerAd(frameLayout, unitId, BannerType, listener);   // full control, one unit
GamechiefAds.loadRandomInterstitialAd(activity, listener);    // click-driven; + loadRandomInterstitialDoubleAd
GamechiefAds.loadRewardedAd(activity, listener);
AdsUi.loadIntroNativeAd(frameLayout);  AdsUi.loadFullScreenNativeAd(activity, listener);   // ui module
```
Single vs double is chosen by the method called (the app can use its own Remote Config flag). Double = both ads requested together from
their own unit sets and SHOWN AT THE SAME TIME (stacked), so closing the top one reveals the other at once. The random interstitial is paced
by `minGapSeconds` and `everyNCalls`; when it loads at runtime the loading dialog is ALWAYS shown (default dialog built in, found by name from
the ui module). Optional custom spots: `loadInterstitialAd(activity, AdPlacement, listener)` and the preload trio. Auto-refill is OFF.

## Bugs in the owner's current code that the new library must NOT reproduce
(see `reference/` for the original code)
1. `showInterstitialAdWithLoader(activity, null)` -> NPE (listener dereferenced when null). Listener must be optional.
2. Double interstitial: two loads started at once, both can show. Request the second only if needed / show at most one.
3. Splash double app-open: second ad shown immediately after first and navigation can run twice. Show second only after first is dismissed, navigate once.
4. Splash waterfall can take 15 s (3 x 5 s) vs a 12 s timeout: use per-tier timeouts (~3 s) and an overall budget. Optional: parallel tiers, first success wins.
5. Deprecated `@OnLifecycleEvent`, `CONNECTIVITY_ACTION` receiver: use DefaultLifecycleObserver / ProcessLifecycleOwner and a network callback (or leave network handling to the app).
6. Static mutable globals for flags: replace by `AdState` + config. Banners: reuse and destroy with lifecycle; remove shimmer on failure too.

## Next-Gen SDK facts (from Google docs on 2026-11-04; VERIFY against current docs before relying on them)
- Latest 1.6.0 (2026-10-22). `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0`. minSdk 24. Kotlin >= 1.9, AGP >= 8.3.0, Java 11 ok. Compile SDK 37 and target SDK 37 (owner request); library minSdk stays 24 (the SDK floor).
- Remove `play-services-ads`; mediation adapters still depend on it, so apps need a global exclude of `com.google.android.gms:play-services-ads` and `play-services-ads-lite`.
- Init is REQUIRED before loading ads, call off the main thread: `MobileAds.initialize(context, InitializationConfig.Builder("APP_ID").build()) { ... }`.
  App id passed in code; UMP still needs the app id `<meta-data>` in the manifest. `onInitializationFailed(AdError)` exists since 1.4.0.
- Requests: `AdRequest.Builder("AD_UNIT_ID").build()`; load has NO Context param: `InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> { onAdLoaded / onAdFailedToLoad(LoadAdError) })`.
  Set `ad.adEventCallback = object : InterstitialAdEventCallback {...}` AFTER load. `show(activity)` unchanged. Same shape for `AppOpenAd`.
- Native: `NativeAdLoader.load(NativeAdRequest.Builder(unitId, listOf(NativeAd.NativeAdType.NATIVE)).build(), NativeAdLoaderCallback)`; view `com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView`, `registerNativeAd(nativeAd, mediaView)`.
- Banner: `AdView` + `loadAd()` (BannerAd.load/getView deprecated since 0.22). Adaptive sizes `AdSize.getLargePortraitAnchoredAdaptiveBannerAdSize()` etc.
- Preloading classes named in release notes: `AdPreloader`, `BannerAdPreloader`, `PreloadConfiguration`, `AdPreloader.start()`, `getNumAdsAvailable()`. Per-format (interstitial/app open) preloader class names were NOT confirmed: check docs/Javadoc; if unclear, implement preload yourself in `AdPool`.
- Renames: error domain `com.google.android.libraries.ads.mobile.sdk`; `setAgeRestrictedTreatment()` replaces child-directed/under-age tags (1.1.0).
- Old and new SDK cannot be assumed to coexist in one app: each app migrates fully.

## Build phases (do in order; commit after each)
1. Gradle project (`gamechiefads-core`, `gamechiefads-ui`, `sample`), publishing setup like PlaySubs (maven-publish; GitHub Packages workflow on release tag).
   Core logic with **unit tests**: AdPolicy, Waterfall (fake loaders: success on tier 2, timeouts, all fail), AdState, AdsConfig/RemoteOverrides parsing.
2. `sdk/` sources, Consent, SdkInit, AdPool.
3. Flows: InterstitialController (on-demand first, then preload), AppOpenController, SplashFlow, ScreenTracker.
4. ui module: ShimmerView, banner, native, loader dialog.
5. Sample app (test unit ids) + README (setup lines only) + docs/USAGE.md.
6. Verify: `./gradlew test assembleRelease :sample:assembleDebug`, lint; run the sample on a device/emulator with test ads: splash flow, random interstitial via lifecycle,
   preload path, premium toggle, consent denied, offline, timeout.

## Open items to ask the owner
- Does any app use third-party mediation adapters? (affects migration notes)
- Public vs private distribution (PlaySubs repo was made public; consider JitPack so teammates need no token).
