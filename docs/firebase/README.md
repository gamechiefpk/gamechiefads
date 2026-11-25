# Publishing the ads rules on Firebase Remote Config

| File | Use |
|---|---|
| [`remoteconfig.template.json`](remoteconfig.template.json) | A full Remote Config template with every parameter below. Deploy it with the Firebase CLI. |
| [`gamechief_ads_config.json`](gamechief_ads_config.json) | The value of the optional JSON parameter `gamechief_ads_config` (advanced rules). |

## Publish

Console: **Engagement > Remote Config > Add parameter** for each row below (or only the ones you want), then **Publish changes**.
CLI: `cp docs/firebase/remoteconfig.template.json remoteconfig.template.json`, then `firebase deploy --only remoteconfig`.

A parameter you do not publish (or leave empty) is "not set": the app's own setting stays. **Timeouts and retry waits are never remote**: they are fixed inside the library
(no ad = no new request for that spot for 60 s, never a request loop).

## The simple keys (one value each, no JSON)

| Key | Type | Meaning |
|---|---|---|
| `splashType` | text | `appopen` or `interstitial`: which ad `GamechiefAds.loadSplashAd(...)` shows |
| `splashDoubleEnable` | bool | double ad on the splash. Your value is the master both ways, even over the code |
| `splashBannerAd` | bool | the splash banner on or off (default true) |
| `langaugeSelectionNativeType` | text | size of the language screen native: `tiny`, `small`, `smallMedia`, `medium`, `large` (the correct spelling `languageSelectionNativeType` works too) |
| `introScrBigNativeEnable` | bool | full-screen native pages in the intro slider (default false) |
| `noOfBigNativeAdsInIntro` | number | how many of them, spread evenly (needs `.introSlideNativeUnit(id)`) |
| `interstitialOnPremiumDismiss` | bool | `GamechiefAds.showInterstitialOnPremiumDismiss(...)` shows the random interstitial only when true |
| `homescrBannerType` | text | type for `GamechiefAds.loadHomeBannerAd(...)`: `adaptive`, `adaptiveLarge`, `collapsible`, `collapsibleTop`, `mediumRectangle`... |
| `bannerAdType` | text | type of the normal banner (`loadBannerAd`), same values |
| `randomClickInterstitialAd` | bool | the random interstitial on or off |
| `doubleInterstitialAdEnable` | bool | double random interstitial. A double loads its second ad with the same request as the first, at the same time: no extra delay |
| `randomClickAdInterval` | number | every N-th click and every N-th new screen |
| `randomClickTimeGap` | number | seconds between two random interstitials |
| `randomClickAdAutoEnable` | bool | the automatic random interstitial over new screens |
| `rewardedAdEnable` | bool | rewarded ads on or off |
| `enableNativeInScroll`, `positionOfAdAt`, `scrollNativeAdType` | bool, number, text | the ONE native ad of `NativeAdAdapter` in a RecyclerView (switch, row, size) |
| `debugShakeEnabled` | bool | debug builds only: shake opens the debug panel |

The template contains the defaults. Publishing it as it is also makes `randomClickAdInterval` 3 and `randomClickTimeGap` 30 for every app that reads the project.

## The optional JSON key `gamechief_ads_config`

Advanced rules: `enabled` (kill switch), `quietSecondsAfterAppOpen`, `tagBanners`, `nativeUnitFallback`, `loaderTitle`/`loaderMessage`/`noAdMessage`, and per slot under `placements`
(`enabled`, `units`, `bannerType`, `nativeSize`, `waterfall`, `flooring`, `double`...). The simple keys above win over it. See [USE_CASES.md](../USE_CASES.md) recipe 21.

## Needs in the app

The `gamechiefads-remoteconfig` module (in the one JitPack line), `google-services.json` and the google-services plugin. Without Firebase the library keeps the rules from your code.
