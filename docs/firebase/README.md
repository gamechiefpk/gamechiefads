# Publishing the ads rules on Firebase Remote Config

Two ready files:

| File | Use |
|---|---|
| [`gamechief_ads_config.json`](gamechief_ads_config.json) | The **value**. Paste it into the Firebase console. |
| [`remoteconfig.template.json`](remoteconfig.template.json) | The same rules as a full Remote Config template, to deploy with the Firebase CLI. |

The parameter name must be exactly **`gamechief_ads_config`** (the library reads that key).

## Option A: Firebase console (no tools)

1. Firebase console, your project, **Engagement > Remote Config**.
2. **Add parameter**: name `gamechief_ads_config`, data type **JSON**, default value: paste the whole content of `gamechief_ads_config.json`.
3. **Add a second parameter**: name `noOfBigNativeAdInIntro`, data type **Number**, default value `0` (how many full-screen native pages the intro slider shows; the template file has it too).
4. Add the three RecyclerView parameters (the template has them): `enableNativeInScroll` (Boolean, default `false`), `positionOfAdAt` (Number, default `4`), `scrollNativeAdType` (String, default `small`).
5. **Save**, then **Publish changes**.

## Option B: Firebase CLI

```bash
firebase login
firebase use <your-project-id>
cp docs/firebase/remoteconfig.template.json remoteconfig.template.json
firebase deploy --only remoteconfig
```

## What the file contains

Every value in it is the library's **default**, so publishing it as it is changes nothing. It never contains ad unit ids: the units stay in your app.
Edit a value and publish again to change it for every app that reads this project. Apps fetch at most once an hour (`remoteFetchIntervalSeconds`), so
a change reaches users on their next fetch, with no app update.

| Key | Meaning |
|---|---|
| `enabled` | `false` = every ad off (kill switch) |
| `noFillRetrySeconds` | wait before the same spot asks again after "no ad" |
| `quietSecondsAfterAppOpen` | no interstitial for this long after an app-open ad closes |
| `tagBanners` | banners and natives from layout tags (`gads_banner`, `gads_native:...`) |
| `loaderTitle`, `loaderMessage`, `noAdMessage` | dialog texts |
| `global.*` | timeouts in ms: splash, one waterfall, SDK start, second ad of a double, "no ad" message |
| `placements.splash.double`, `placements.splash_interstitial.double`, `placements.random.double` | the **double** ads (a second ad shown with the first). OFF by default. **Your value here is the master**: `true` shows doubles even if the app code asks for a single, `false` shows a single ad even if the code asks for a double. Needs a second unit declared in the app, otherwise it is a single ad |
| Parameter `noOfBigNativeAdInIntro` (a NUMBER, not inside the JSON) | how many full-screen native pages the `IntroPager` puts between the intro slides. `0` = none (default), `1`, `2`... spread evenly. Needs `.introSlideNativeUnit(id)` in the app |
| Parameters `enableNativeInScroll` (BOOLEAN), `positionOfAdAt` (NUMBER), `scrollNativeAdType` (STRING) | the ONE native ad that `NativeAdAdapter` puts in a RecyclerView: the switch (default false = no ad and no request), the row (default 4), the size (tiny, small, small_media, medium, large). They also work while the screen is open: switching off removes the row smoothly, no new request on scroll |
| `nativeUnitFallback` (root key, true/false) | a native spot without ad units borrows the units of another declared native spot (default true) |
| `placements.app_open.double` | the double app-open ad when the user comes back (second unit: `.appOpenDoubleUnit(id)`), same rules as the other doubles |
| `placements.fullscreen_native.enabled` | the full-screen native ad. OFF; set `true` to turn on |
| `placements.random` | `every` (every Nth new screen, 0 = off), `gap` (seconds between ads), `everyNCalls` (button calls), `auto` |
| `placements.<slot>.enabled` | switch one slot off: `splash`, `splash_interstitial`, `splash_banner`, `app_open`, `banner`, `exit_banner`, `random`, `intro_native`, `fullscreen_native`, `list_native`, `rewarded` |

## Optional keys (add them only when you want to override the app's code)

```json
"placements": {
  "banner":        { "bannerType": "COLLAPSIBLE_BOTTOM", "flooring": false },
  "intro_native":  { "nativeSize": "small" },
  "list_native":   { "nativeSize": "tiny" },
  "random":        { "double": true, "waterfall": "parallel", "tierTimeoutMs": 3000 },
  "rewarded":      { "units": ["ca-app-pub-XXXX/1111", "ca-app-pub-XXXX/2222"] }
}
```

`bannerType`: `ADAPTIVE`, `ADAPTIVE_LARGE`, `INLINE_ADAPTIVE`, `COLLAPSIBLE_BOTTOM`, `COLLAPSIBLE_TOP`, `BANNER`, `LARGE_BANNER`, `MEDIUM_RECTANGLE`, `FULL_BANNER`, `LEADERBOARD`.
`nativeSize`: `tiny`, `small`, `small_media`, `medium`, `large`, `full` (the app shows small cards by default; big sizes only when you set them here or in code). `units` replaces that slot's ad units (first id = first floor).
All keys: [USE_CASES.md](../USE_CASES.md) recipe 21.

## Needs in the app

The `gamechiefads-remoteconfig` module (included in the one JitPack line), `google-services.json` and the google-services Gradle plugin. Without Firebase set up the library
keeps the rules from your code. Keys that need library `v1.0.3` or newer: `tagBanners`, `nativeSize`, `ADAPTIVE_LARGE`; older versions ignore keys they do not know.
