# GamechiefAds: reference

The quick recipes are in [USE_CASES.md](USE_CASES.md). This page lists everything once, with the exact names.

## 1. The two steps

1. `GamechiefAds.init(application, config)` once, in `Application.onCreate`. The config holds **all your ad units**.
2. In screens call `GamechiefAds.loadXxxAd(...)`. `GamechiefAds` is a static object: use it from any Activity.

Modules: `gamechiefads-core` (all logic, no UI), `gamechiefads-ui` (shimmer, loading dialog, native ads), `gamechiefads-remoteconfig` (Firebase Remote Config),
`gamechiefads-mediation` (the five partner adapters). The one JitPack line pulls all four (see the README).

## 2. Config: where each ad unit goes

"Units" with several ids are **floors**: the first id is requested first (high floor), then the next, then the next.

| Config method | Ad | Floors |
|---|---|---|
| `splashAppOpenUnits(...)` | Splash app open | yes |
| `splashAppOpenDoubleUnit(id)` | 2nd splash app open of a double | no, one unit |
| `splashInterstitialUnits(...)` / `splashInterstitialDoubleUnit(id)` | Splash interstitial (and its double, one unit) | yes / no |
| `splashBannerUnits(...)` | Splash banner, always LARGE_BANNER 320x100 | yes |
| `appOpenUnit(id)` | App open when the user returns (automatic) | no, one unit |
| `bannerUnits(...)` | Banner, all normal screens | yes |
| `exitBannerUnits(...)` | Exit-screen banner (medium rectangle unless `.bannerType(...)`) | yes |
| `randomInterstitialUnits(...)` | Interstitial on random clicks | yes |
| `randomInterstitialDoubleUnit(id)` | 2nd interstitial of a double | no, one unit |
| `introNativeUnits(...)` | Small native, intro screens | yes |
| `fullScreenNativeUnit(id)` | Full-screen native like an interstitial | no, one unit |
| `rewardedUnits(...)` | Rewarded | yes |

Options you can chain after a slot method (they apply to that slot):

```java
.randomInterstitialUnits(HF, MF, NORMAL)
    .minGapSeconds(30)              // at least 30 s since the last ad
    .everyNCalls(3)                 // only on every 3rd call
    .excludeScreens(IntroActivity.class)   // never when called from these screens
    .tierTimeoutMs(3000)            // time for one unit (default 3 s)
    .unitTimeoutMs(HF, 1500)        // give one unit its own time (the high floor rarely fills)
    .skipUnitAfterFails(3, 120)     // 3 misses in a row: skip that unit for 120 s, then try it again
    .disableUnit(MF)                // keep the id, stop requesting it
    .flooring(false)                // no floors: ONE request, to the LAST unit
    .waterfallMode(WaterfallMode.PARALLEL)   // ask all units at once, first ad wins
.bannerUnits(...).bannerType(BannerType.COLLAPSIBLE_BOTTOM)
.rewardedUnits(...).premiumGetsReward(false)
```

Global options: `appId`, `splashTimeoutMs` (default 10 s, the hard limit of a splash), `overallBudgetMs` (default 8 s, the
limit of one whole floors walk), `initTimeoutMs`, `doubleSecondWaitMs` (how long to wait for the 2nd ad of a double,
default 2.5 s), `defaultLoaderText(title, message)`, `testIdsInDebug(false)` (to try your real ids in a debug build; debug
builds otherwise always use Google test ids).

Your own extra spots (optional): `.interstitial(AdPlacement)`, `.rewarded(...)`, `.banner(...)`, `.nativeAd(...)` with
`.units(...)`, called with `GamechiefAds.loadInterstitialAd(activity, placement, listener)` and friends.

## 3. Every method

| Method | What it does |
|---|---|
| `init(app, config)` | Once. |
| `setPremium(boolean)` | Premium: no ads at all. |
| `applyRemoteConfig(json)` | Remote Config overrides (section 5). |
| `setLoadingDialog(ui)` / `setLoadingText(title, message)` | Replace / re-text the loading dialog. |
| `skipNextAppOpenAd()` | Skip the next app-open ad. |
| `loadSplashAppOpenAd(activity, callback)` | Splash. `callback` gets `ads`: call `ads.show(activity, onDone)`. |
| `loadSplashAppOpenDoubleAd(activity, callback)` | Same, two ads together. |
| `loadSplashInterstitialAd(activity, callback)` | Same with an interstitial. |
| `loadSplashInterstitialDoubleAd(activity, callback)` | Same, two interstitials together. |
| `loadSplashBannerAd(frame[, listener])` | Large banner into a FrameLayout. |
| `loadBannerAd(frame[, listener])` | Banner, all screens. |
| `loadExitBannerAd(frame[, listener])` | Exit banner. |
| `loadCustomBannerAd(frame, unitId, type[, listener])` | Your own unit, size and type. |
| `loadRandomInterstitialAd(activity, listener)` | Click-driven interstitial. |
| `loadRandomInterstitialDoubleAd(activity, listener)` | Same, two ads together. |
| `loadRewardedAd(activity, listener)` | Rewarded. Reward only in `onRewardEarned`. |
| `AdsUi.loadIntroNativeAd(frame[, listener])` | Small native (ui module). |
| `AdsUi.loadFullScreenNativeAd(activity[, listener])` | Full-screen native (ui module). |

All listeners are optional (`null` is fine) and all callbacks run on the main thread. `onComplete` always runs exactly once.

## 4. How a double ad works

Both ads are requested at the same time. The first uses its units with floors; the second is ONE request to its own single unit, **never floors** (paste the same id as the first ad, or another one; if you give none, the last unit of the first set is used). When both are loaded (the
second one is waited for at most `doubleSecondWaitMs`; if it is missing, one ad is shown) they are shown **at the same time**,
stacked. The user closes the top ad and the other is already underneath, so it feels like the first ad never closed. The
"ad showing" flag is held until the last one closes, and your callback runs once, after the last ad.

## 5. Remote Config (optional)

The library reads Firebase Remote Config by itself (section 11) and applies this JSON from the key `gamechief_ads_config`. You can also call `GamechiefAds.applyRemoteConfig(json)` yourself. The core has no Firebase
dependency. Bad JSON is ignored. It can only change slots you declared, never add new ones.

```json
{"enabled": true,
 "loaderTitle": "Ad is loading", "loaderMessage": "Please wait...",
 "placements": {"random": {"units": ["a","b"], "secondUnit": "c", "everyNCalls": 3, "gap": 30,
                           "enabled": true, "tierTimeoutMs": 3000, "tierTimeouts": {"a": 1500},
                           "flooring": true, "waterfall": "parallel", "disabledUnits": ["b"],
                           "skipAfterFails": 3, "skipForSec": 120, "bannerType": "MEDIUM_RECTANGLE",
                           "premiumGetsReward": true, "loaderTitle": "...", "loaderMessage": "..."}}}
```

Slot names: `splash`, `splash_interstitial`, `splash_banner`, `app_open`, `banner`, `exit_banner`, `random`, `intro_native`,
`fullscreen_native`, `rewarded`. Single or double is chosen by the method you call, so keep your own Remote Config flag:
`if (flag) loadSplashAppOpenDoubleAd(...) else loadSplashAppOpenAd(...)`.

## 6. The loading dialog

When an interstitial or rewarded ad has to load at runtime, the "Ad is loading, please wait" dialog is **always** shown
(a default one is built in when the ui module is present). Texts: `setLoadingText`, the config, Remote Config, or per slot
`.loaderText(title, message)`. Look: `GamechiefAds.setLoadingDialog(DefaultLoaderDialog.builder()...build())`
(`title`, `message`, `tips`, `accentColor`, `cardColor`, `textColor`, `cornerRadiusDp`, `dimAmount`, `blurBackground`).

## 7. Guarantees

- Consent (UMP) runs before the SDK starts; denied consent is not asked again for 60 s.
- Fail open: any error means "no ad". Nothing blocks the user: every wait has a timeout.
- Only one full-screen ad flow at a time (a second call is refused, a 5 s watchdog frees the flag if the SDK never answers).
- Premium turned on while an ad loads: the ad is dropped. Ads only show on the visible, alive Activity.
- No random interstitial on back presses.
- Callbacks always come on the main thread, once.

## 8. Migrating an app

1. Remove `play-services-ads`; add the dependency and the excludes from the README. Keep the `APPLICATION_ID` meta-data.
2. Delete your old app-open manager, static ad flags and per-activity counters. Put the ad units in the config (section 2).
3. Splash: `loadSplashAppOpenAd(...)` (or the double / interstitial version). Remove the `CONNECTIVITY_ACTION` receiver:
   a missing network is just "no ad".
4. Replace each old interstitial call with `loadRandomInterstitialAd` (or your own custom spot).
5. Third-party mediation adapters must be the Next-Gen compatible versions (the mediation module has them).

## 9. Debug trace log

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


## 10. Mediation (AdMob mediation with network adapters)

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


## 11. Remote Config (built in)

The `gamechiefads-remoteconfig` module (part of the one-line dependency) implements `RemoteConfigSource` with Firebase Remote
Config. The core finds it by name, so the app writes no Firebase Remote Config code. The app needs `google-services.json` and
the google-services plugin once (README). If Firebase is not set up, the library runs without it: getters return your defaults
and fallbacks, and `isRemoteConfigAvailable()` is false.

| What | How |
|---|---|
| Ad rules from the console | Key `gamechief_ads_config` (change: `.remoteConfigKey("key")`, off: `.remoteConfigKey(null)`), JSON of section 5 |
| Add your own value | Config: `.remoteDefault(key, value)` or `.remoteDefaults(map)`; later: `GamechiefAds.addRemoteDefaults(map)` |
| Read a value, anywhere | `GamechiefAds.getRemoteString / getRemoteBoolean / getRemoteLong / getRemoteDouble(key, fallback)` |
| Fetch now | `GamechiefAds.fetchRemoteConfig(listener)` |
| Know when values changed | `GamechiefAds.setRemoteConfigListener(runnable)` (fetch or real-time update) |
| Fetch interval | `.remoteFetchIntervalSeconds(s)`, default 3600 (the server throttles: lower it only while testing) |
| Another backend | `.remoteConfigSource(yourRemoteConfigSource)` |

Value order: the server value, then your default from the config, then the fallback you pass to the getter. Getters never throw
and work before the first fetch (they return the last fetched values of the previous run, or your defaults). Supported
types: String, Boolean, Int, Long, Double. `applyRemoteConfig(json)` from section 3 still exists for manual use.
