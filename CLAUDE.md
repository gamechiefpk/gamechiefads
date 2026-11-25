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
`randomInterstitialUnits(...)`, `randomInterstitialDoubleUnit(id)`, `exitBannerUnits(...)`, `appOpenUnit(id)`, `introNativeUnits(...)`,
`fullScreenNativeUnit(id)`, `rewardedUnits(...)`...). One shared https rules file for ALL apps: `.remoteConfigUrl(url)` (layers: config < file < Firebase < applyRemoteConfig); library CODE cannot change without a release (Play policy), SETTINGS can, so new behaviour ships behind a setting. Remote Config is built in (Firebase, module `gamechiefads-remoteconfig`, found by name): `remoteDefault(key, value)` in the config, `GamechiefAds.getRemoteString/Boolean/Long/Double(key, fallback)` anywhere; the ads rules come from key `gamechief_ads_config`. Several ids = floors (first id first). `appOpenUnit` and `fullScreenNativeUnit`
take ONE unit, no floors. The second ad of a double is ONE unit, no floors (the same id as the first is fine). Full-screen ads never overlap (one shared ad-showing flag), and after an app-open ad is CLOSED interstitials stay quiet `quietSecondsAfterAppOpen` (default 30 s). Floors are ONE pass (each unit once); no fill = the loading dialog turns into the message `noAdMessage` for ~2 s (toast only as a fallback), then the listener runs, and the placement waits `noFillRetrySeconds` (default 5) before a new pass: never a request loop.
```java
GamechiefAds.init(app, config);   GamechiefAds.setPremium(boolean);   GamechiefAds.applyRemoteConfig(json);
GamechiefAds.setLoadingDialog(ui); GamechiefAds.setLoadingText(title, message); GamechiefAds.skipNextAppOpenAd();
GamechiefAds.loadSplashAppOpenAd(activity[, double], callback);   // + loadSplashInterstitialAd(activity[, double], callback); double = Remote Config placements.splash.double is the MASTER both ways (true or false beats what the code passes); without it the boolean or config .doubleAd decides; OFF by default; no declared second unit = quiet single ad; the old ...DoubleAd methods are deprecated aliases
GamechiefAds.loadSplashBannerAd(frameLayout);                 // always LARGE_BANNER, ONE unit (splashBannerUnit), no floors
GamechiefAds.loadBannerAd(frameLayout, BannerType[, isEnableFlooring]);  // type for one call; app default: AdsConfig .defaultBannerType(type); tag gads_banner:<type>
GamechiefAds.loadBannerAd(frameLayout[, isEnableFlooring]);  GamechiefAds.loadExitBannerAd(frameLayout[, isEnableFlooring]);   // false = one request, last unit
// no code at all: android:tag="gads_banner" | gads_banner_fast | gads_exit_banner | gads_exit_banner_fast | gads_splash_banner on a FrameLayout (TagBanners; .tagBanners(false) or RC {"tagBanners":false} = off)
// optional base screen in the ui module: `AdsActivity extends AppCompatActivity` with open loadBannerAd/loadExitBannerAd/loadRandomInterstitialAd/loadRewardedAd
// the loading dialog stays until the full-screen ad is really on screen (slow networks), not just until it is loaded
GamechiefAds.loadCustomBannerAd(frameLayout, unitId, BannerType, listener);   // full control, one unit
GamechiefAds.loadRandomInterstitialAd(activity, listener);    // the random interstitial on YOUR button; it is also AUTOMATIC over every 3rd new screen (no call needed; everyNScreens(0) or autoShow(false) = off; runtime switch: setAutoRandomInterstitialEnabled(boolean)); + loadRandomInterstitialDoubleAd
GamechiefAds.loadRewardedAd(activity, listener);
GamechiefAds.showDebugPanel(activity) / openQaScreen(activity): DEBUG BUILDS ONLY (no-op in release): bottom sheet / full screen with Logs, Setup, Config, Test tabs (ui module).
IntroPager (ui module): intro slider, you give layouts (setPages(R.layout.a, ...), setOnFinished); Get started on the last page; full-screen native pages between slides = Firebase number parameter noOfBigNativeAdInIntro (0 default), config .introSlideNativeUnit(id) (one unit, no floors), each ad requested once and preloaded, close button after 3 s, touches shielded meanwhile.
GamechiefAds.checkSetup() / setTestMode(boolean) / openAdInspector(): QA tools (setup checker also prints in logcat in debug builds); docs/starter/ = copy-paste starter. Intro and language natives default to SMALL_MEDIA.
AdsUi.loadIntroNativeAd(frameLayout);  AdsUi.loadFullScreenNativeAd(activity, listener);   // ui module
NativeListAds ads = new NativeListAds(NativeSize.SMALL);  // config .listNativeUnit(id): ONE unit, no floors; positions: new NativeListPositions() (first ad after 4 rows, then every 8); RC "nativeSize" on any native placement changes its size remotely;  // native ads in a RecyclerView: ads.bind(rowFrameLayout, adIndex) in onBindViewHolder, ads.destroy() in onDestroy; each ad requested ONCE
AdsUi.loadNativeAd(frameLayout, "placement_name", NativeSize.MEDIUM[, style, listener]);   // any native spot, only a FrameLayout needed (tag: gads_native:<placement>[:<size>])
```
Single vs double: OFF by default; turn it on with the slot switch (Remote Config `double`, or `.doubleAd(true)`) or a boolean argument. EXTRA ads are off until switched on: doubles, `fullScreenNativeUnit` (RC `placements.fullscreen_native.enabled`); native sizes default to small (language screen: SMALL_MEDIA, via `languageNativeUnits` + `AdsUi.loadLanguageNativeAd`), big sizes only on request or RC `nativeSize`. Double = both ads requested together from
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
