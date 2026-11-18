# Ad formats and where to use them: follow your app's flow

This page walks through a normal app from the first screen to the last and says which ad goes where, with the code.
At the end there are two boxes: every **banner** type and every **native** size.
All ad units are given once in `Application` (see [USE_CASES](USE_CASES.md) recipe 1). `GamechiefAds` is static: call it from any Activity.

| Screen | Ad | Call |
|---|---|---|
| 1. Splash | Large banner + app open **or** interstitial (single or double) | `loadSplashBannerAd`, `loadSplashAppOpenAd`, `loadSplashInterstitialAd` |
| 2. Language selection | Native (medium or large) | `NativeAdTemplateView` |
| 3. Intro / onboarding screens | Small native at the bottom | `AdsUi.loadIntroNativeAd` |
| 4. Home and other screens | Banner | `loadBannerAd` or the tag `gads_banner` |
| 5. Exit dialog | Banner (300x250) or a native | `loadExitBannerAd` |
| 6. Button clicks / transitions | Random interstitial (automatic + buttons) | `loadRandomInterstitialAd` |
| 7. "Watch ad for coins" | Rewarded | `loadRewardedAd` |
| 8. User returns to the app | App open (automatic) | nothing to call |
| 9. Between big steps | Full-screen native | `AdsUi.loadFullScreenNativeAd` |

Everything is **one pass** over the floors (high, medium, normal, each once), fails open (no ad = the app just continues), and premium users
see nothing: `GamechiefAds.setPremium(true)`.

---

## Setup: all units in one place

```java
GamechiefAds.init(this, AdsConfig.builder()
    .appId("ca-app-pub-XXXX~YYYY")
    .splashBannerUnit(SPLASH_BANNER)                 // one unit
    .splashAppOpenUnits(HF, MF, NORMAL)              // floors
    .splashAppOpenDoubleUnit(UNIT2)                  // only for the double
    .splashInterstitialUnits(HF, MF, NORMAL)         // only if your splash shows an interstitial
    .appOpenUnit(APP_OPEN)                           // when the user returns: one unit, no floors
    .introNativeUnits(HF, MF, NORMAL)                // small native on intro screens
    .nativeAd("language_native").units(HF, MF, NORMAL)   // your own native spot, e.g. the language screen (any name)
    .bannerUnits(HF, MF, NORMAL)                     // home and all normal screens
    .exitBannerUnits(EXIT_BANNER)                    // exit dialog
    .randomInterstitialUnits(HF, MF, NORMAL)         // clicks + automatic over transitions
    .randomInterstitialDoubleUnit(UNIT2)
    .rewardedUnits(HF, MF, NORMAL)
    .fullScreenNativeUnit(FULL_NATIVE)               // one unit
    .build());
```

You write only the lines you use. Several ids = floors, the first id is requested first.

---

## 1. Splash screen

The splash has two ads: a **banner** under the logo (optional) and one **full-screen ad** (app open or interstitial) before the app opens.

```java
// banner: any FrameLayout, always a 320x100 large banner, one unit, no floors
GamechiefAds.loadSplashBannerAd(findViewById(R.id.splashBanner));

// full-screen ad, then open the app. Call your next screen ONLY inside the callback
GamechiefAds.loadSplashAppOpenAd(this, ads ->
    ads.show(this, () -> startActivity(new Intent(this, LanguageActivity.class))));
```

`ads.show` shows the ad and runs your code **once** after it closes. No ad (offline, premium, no fill, time limit) = your code runs at once, so the user is never stuck.

| I want on the splash | Call |
|---|---|
| App open ad (default choice) | `loadSplashAppOpenAd(this, callback)` |
| Two app open ads together ("double") | `loadSplashAppOpenDoubleAd(this, callback)` |
| Interstitial instead of app open | `loadSplashInterstitialAd(this, callback)` |
| Two interstitials together ("double") | `loadSplashInterstitialDoubleAd(this, callback)` |

- **Double:** both ads are requested together and shown on top of each other, so closing the first reveals the second at once. The second uses its own single unit (the same id as the first is fine).
- **Floors off:** add `.flooring(false)` after the units in the config: one request to the last unit (faster).
- **Time limit:** consent dialog (EU), SDK start and the ad together are limited to `splashTimeoutMs` (12 s). Changeable remotely.

Pick single or double with your own Remote Config flag if you want:
`boolean dbl = GamechiefAds.getRemoteBoolean("splash_double", false);` and call the matching method.

## 2. Language selection screen: native ad

You give a `FrameLayout`, the library does the rest: it inflates the native layout for the size you pick, shows a shimmer while loading,
hides the container when there is no ad, and destroys the ad with the screen. No native XML of your own.

```java
AdsUi.loadNativeAd(findViewById(R.id.nativeFrame), "language_native", NativeSize.MEDIUM);   // medium is the usual choice here
```

`language_native` is the name you gave in the config (`.nativeAd("language_native").units(...)`). Each native spot of your app (language, home list,
result screen) gets its own name and its own units. Optional extras: a style and a listener:
`AdsUi.loadNativeAd(frame, "language_native", NativeSize.MEDIUM, NativeStyle.builder().button(0xFF6C4CF1, 0xFFFFFFFF).cornerRadiusDp(16).build(), listener)`.

No code at all: tag the FrameLayout in the layout XML and the library fills it when the screen opens:

```xml
<FrameLayout android:layout_width="match_parent" android:layout_height="wrap_content"
    android:tag="gads_native:language_native:medium" />          <!-- gads_native:<placement>[:<size>] -->
```

Sizes: `tiny`, `small`, `small_media`, `medium` (default when left out), `large`, `full`. All sizes and options are in the **Native box** below.
(The `NativeAdTemplateView` XML view still works if you want the ad inside your own layout tree with its own attributes.)

## 3. Intro / onboarding screens: small native at the bottom

```java
AdsUi.loadIntroNativeAd(findViewById(R.id.nativeContainer));                      // small card, floors from introNativeUnits
AdsUi.loadNativeAd(findViewById(R.id.nativeContainer), "intro_native", NativeSize.SMALL_MEDIA);   // another size, same units
```

Or by tag: `android:tag="gads_native:intro_native:small"`. Reload per page: call it again with the same container; the old ad is replaced and destroyed.

## 3b. Native ads in a scrolling list (RecyclerView): tiny, small or any size

Use `NativeListAds`, one object per list. A list ad loads **without floors from ONE ad unit**: one request per ad, nothing more.
Do **not** call `AdsUi.loadNativeAd` inside `onBindViewHolder`: a recycled row is detached, which destroys its ad, so every scroll back would send a new request.
`NativeListAds` requests each ad **once**, keeps it, and shows it again when the row comes back. A failed ad is not retried (its row collapses).

**Recommended setup** (the defaults already follow it):

| Setting | Recommended | Where |
|---|---|---|
| Ad unit | one unit, no floors | `.listNativeUnit(id)` in the config |
| Size | `SMALL` (or `TINY` between dense rows, `SMALL_MEDIA` with a thumbnail). Avoid `LARGE` and `FULL` in lists | `new NativeListAds(NativeSize.SMALL)` |
| Spacing | first ad after 4 rows, then one every 8 rows, never as the last row | `new NativeListPositions()` |
| Limit | at most 5 ads per screen (the rest stay empty) | `maxAds`, default 5 |
| Expiry | a kept ad older than 50 minutes is replaced by one new request | `ttlMinutes`, default 50 |
| Premium | no ad rows at all | `new NativeListPositions(4, 8, !isPremium)` |
| Size from the server | `{"placements":{"list_native":{"nativeSize":"tiny"}}}` | Remote Config, no app update |

**1. Config:**

```java
.listNativeUnit(LIST_NATIVE_UNIT)        // ONE unit, no floors
```

**2. The adapter**: an ad row is a row whose root view is a `FrameLayout`:

```java
public class MyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_ITEM = 0, TYPE_AD = 1;
    private final NativeListAds ads;
    private final NativeListPositions pos;                              // where the ad rows go

    MyAdapter(NativeListAds ads, NativeListPositions pos) { this.ads = ads; this.pos = pos; }

    @Override public int getItemCount() { return pos.count(items.size()); }             // items + ad rows
    @Override public int getItemViewType(int position) { return pos.isAd(position) ? TYPE_AD : TYPE_ITEM; }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        if (type == TYPE_AD) {
            FrameLayout frame = new FrameLayout(parent.getContext());            // or an XML file whose root is a FrameLayout
            frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(frame) { };
        }
        return new ItemHolder(...);                                              // your normal row
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (pos.isAd(position)) ads.bind((FrameLayout) holder.itemView, pos.adIndex(position));   // ad number 0, 1, 2...
        else ((ItemHolder) holder).show(items.get(pos.itemIndex(position)));                     // skips the ad rows
    }
}
```

**3. The screen:**

```java
ads = new NativeListAds(NativeSize.SMALL);                  // placement "list_native"; also (placement, size, style, maxAds, ttlMinutes)
recycler.setAdapter(new MyAdapter(ads, new NativeListPositions()));

@Override protected void onDestroy() { ads.destroy(); super.onDestroy(); }      // in a Fragment: onDestroyView
```

**Rotation without new requests.** A rotation recreates the screen and its `NativeListAds`, which would ask again. Keep the object in a `ViewModel`
(`ads = new NativeListAds(...)` as a field, `ads.destroy()` in `onCleared()`), or in a plain Activity return it from `onRetainNonConfigurationInstance()` and read it back with
`getLastNonConfigurationInstance()`; call `destroy()` only when `!isChangingConfigurations()`. The loaded ads are then shown again at once. (The sample screen does this.)

Rules: the ad row's root **must be the FrameLayout** (so a failed ad collapses the whole row); the ad number comes from `pos.adIndex(position)`, never the raw position
(inserting items does not move ads). Remote `nativeSize` may be `tiny`, `small`, `small_media`, `medium`, `large` or `full`; use `full` only where the row can be tall.
Try it in the sample: "Native ads in a RecyclerView (dummy list)".

## 4. Home screen (and every normal screen): banner

Two ways, same result:

```java
GamechiefAds.loadBannerAd(findViewById(R.id.bannerFrame));          // floors
GamechiefAds.loadBannerAd(findViewById(R.id.bannerFrame), false);   // NO floors: one request, fastest (screens the user leaves quickly)
```

```xml
<!-- no code at all: the library fills it when the screen opens -->
<FrameLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:tag="gads_banner" />
```

Tags: `gads_banner`, `gads_banner_fast` (no floors), `gads_exit_banner`, `gads_exit_banner_fast`, `gads_splash_banner`. Banner type of the slot:
`.bannerUnits(...).bannerType(BannerType.COLLAPSIBLE_BOTTOM)`. A shimmer shows while loading, the container hides itself without an ad, and
the banner is destroyed with its screen.

One banner with your own unit, size and type, nothing in the config:

```java
GamechiefAds.loadCustomBannerAd(frameLayout, "ca-app-pub-.../banner", BannerType.MEDIUM_RECTANGLE);
```

## 5. Exit dialog: banner (or native)

```java
View v = getLayoutInflater().inflate(R.layout.dialog_exit, null);       // contains a FrameLayout R.id.exitBanner
GamechiefAds.loadExitBannerAd(v.findViewById(R.id.exitBanner));          // 300x250 medium rectangle by default
new AlertDialog.Builder(this).setView(v)
    .setPositiveButton("Exit", (d, w) -> finish()).setNegativeButton("Stay", null).show();
```

The exit slot is a **medium rectangle (300x250)** unless you set `.exitBannerUnits(...).bannerType(...)`. For a native instead: put a
`NativeAdTemplateView` with `app:gads_size="medium"` (or `full` on a full exit page) in the dialog layout.

## 6. Random click ads (interstitial)

Automatic: every 3rd **new** screen the library shows an interstitial over the transition, with the loading dialog, at least 30 s after the last ad.
Nothing to call. Switch it at run time: `GamechiefAds.setAutoRandomInterstitialEnabled(false)`.

Extra buttons (the library decides if it is time):

```java
button.setOnClickListener(v -> GamechiefAds.loadRandomInterstitialAd(this, new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { openNextScreen(); }     // runs once, shown or not
}));
GamechiefAds.loadRandomInterstitialDoubleAd(this, listener);                       // two ads stacked
```

The loading dialog ("Ad is loading, please wait") is always shown for an ad that loads at run time, and it stays until the ad is really on screen.
No ad = the dialog says "No ad available", then your listener runs. Interstitials never overlap an app open ad and stay quiet 30 s after one closes.
Pass `null` as the listener if you do not need one.

## 7. Rewarded ads

```java
GamechiefAds.loadRewardedAd(this, new RewardListener() {
    @Override public void onRewardEarned(int amount, String type) { addCoins(10); }   // give the reward ONLY here
    @Override public void onNotAvailable(String reason) { }                            // the dialog already said "No ad available"
});
```

Closing early gives no reward. A premium user gets `onRewardEarned` without an ad (`.premiumGetsReward(false)` to stop that).

## 8. App open when the user returns

Automatic. When the app comes back to the foreground the library shows the app open ad (one unit, `appOpenUnit`), with a cooldown, never over another ad, and never right after one.
Before a flow that leaves the app (billing, camera, permission) call `GamechiefAds.skipNextAppOpenAd()` once.

## 9. Full-screen native

Like an interstitial but a native ad that covers the screen, with a close button after 3 seconds. One unit, no floors.

```java
AdsUi.loadFullScreenNativeAd(this, new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { openNextScreen(); }
});
```

Options (`FullScreenNativeAd.Options`): `closeDelaySeconds`, `style`, `loader`.

---

# Box 1: Banner ads, all types and sizes

**Change the default type for the whole app** (for example collapsible instead of adaptive): `.bannerUnits(HF, MF, NORMAL).defaultBannerType(BannerType.COLLAPSIBLE_BOTTOM)` in the config. It applies to the normal-screen banner; the exit banner (300x250) and the splash banner (320x100) keep their own sizes. One screen can still choose its own: `GamechiefAds.loadBannerAd(frame, BannerType.ADAPTIVE)` or the tag `gads_banner:adaptive`. From Remote Config: `{"placements":{"banner":{"bannerType":"COLLAPSIBLE_BOTTOM"}}}`. Collapsible banners belong at the **bottom** of the screen (`COLLAPSIBLE_TOP` at the top) and fill from Google demand only.

Set the type of a slot with `.bannerType(...)` in the config (or `"bannerType"` in the remote rules), or per call with `loadCustomBannerAd(frame, unit, type)`.

| `BannerType` | Size | Best for |
|---|---|---|
| `ADAPTIVE` (default) | full width, height chosen by Google (about 50 to 150 dp) | home and all normal screens |
| `INLINE_ADAPTIVE` | full width, height decided when it loads | inside scrolling lists or content |
| `COLLAPSIBLE_BOTTOM` | adaptive, expands upwards | banner pinned at the **bottom** of the screen (Google demand only) |
| `COLLAPSIBLE_TOP` | adaptive, expands downwards | banner pinned at the **top** (Google demand only) |
| `BANNER` | 320 x 50 | small footer |
| `LARGE_BANNER` | 320 x 100 | splash banner (fixed for `loadSplashBannerAd`) |
| `MEDIUM_RECTANGLE` | 300 x 250 | exit dialog (default of the exit slot), result screens |
| `FULL_BANNER` | 468 x 60 | tablets |
| `LEADERBOARD` | 728 x 90 | tablets |

| Which call | Units | Floors | Type |
|---|---|---|---|
| `loadSplashBannerAd(frame)` | `splashBannerUnit` | no, one unit | `LARGE_BANNER` always |
| `loadBannerAd(frame)` | `bannerUnits` | yes | the slot's type (adaptive) |
| `loadBannerAd(frame, false)` | `bannerUnits` | no: one request, last unit | same |
| `loadExitBannerAd(frame[, false])` | `exitBannerUnits` | yes / no | `MEDIUM_RECTANGLE` unless set |
| `loadCustomBannerAd(frame, unit, type)` | the unit you pass | no | the type you pass |
| tag `gads_banner` / `gads_banner_fast` / `gads_exit_banner` / `gads_exit_banner_fast` / `gads_splash_banner` | as above | as above | as above |

Rules for every banner: the container can be any `FrameLayout` with `wrap_content` height; a shimmer shows while it loads; it hides itself when there is no ad or the user is premium;
it is destroyed with its screen; a second call on a container that already has a banner reuses it.

---

# Box 2: Native ads, usage and sizes

Native ads need the `ui` module (included in the one JitPack line). Six ways to show one:

| How | Use for |
|---|---|
| `AdsUi.loadNativeAd(frame, placement, size)` | **any screen, the easy way**: give a FrameLayout, no XML of yours |
| tag `android:tag="gads_native:<placement>[:<size>]"` on a FrameLayout | no code at all, filled when the screen opens |
| `<NativeAdTemplateView app:gads_size=... app:gads_placement=...>` in XML | any screen: language, lists, results, dialogs (loads by itself) |
| `AdsUi.loadIntroNativeAd(frame)` | intro screens: small card, one line of code |
| `NativeListAds` (see 3b) | native ads inside a RecyclerView: each ad requested once, kept while scrolling |
| `AdsUi.loadFullScreenNativeAd(activity, listener)` | full-screen native like an interstitial |

**Sizes** (`NativeSize`, XML `app:gads_size`):

| Size | XML value | Height | What it shows | Use for |
|---|---|---|---|---|
| `TINY` | `tiny` | about 60 dp | one line: icon, headline, text, small button | under lists, toolbars |
| `SMALL` | `small` | about 140 dp | icon, headline, rating, body, full-width button, no media | intro screens (default of `loadIntroNativeAd`) |
| `SMALL_MEDIA` | `small_media` | about 148 dp | media thumbnail (video ok) left, text and button right | home lists, result rows |
| `MEDIUM` | `medium` | about 330 dp | media on top, icon, headline, rating, body, button | language screen, result screens |
| `LARGE` | `large` | about 480 dp | tall media, bigger text, store and price, large button | dedicated ad pages |
| `FULL` | `full` | fills its parent | media takes the spare height | dialogs, full exit pages |

**Options of the view**

| Option | XML | Code | Default |
|---|---|---|---|
| Size | `gads_size` | `setSize(NativeSize)` | `medium` |
| Placement (which units) | `gads_placement` | `setPlacement(name)` / `load(name)` | none |
| Load when attached | `gads_autoLoad` | `setAutoLoad(boolean)` | true |
| Hide when no ad | `gads_hideOnFail` | `setHideOnFail(boolean)` | true (GONE) |
| Colours and corners | colour names in `res/values/colors.xml` | `setStyle(NativeStyle.builder()...)` | light and dark built in |
| Size from the server | | Remote Config `{"placements":{"<placement>":{"nativeSize":"tiny"}}}` overrides the size in code, for any native placement (intro, language, list...) | the size in code |
| Result | | `setListener(NativeAdTemplateView.Listener)` (`onLoaded`, `onFailed`) | none |

`NativeStyle.builder()`: `background`, `stroke`, `headlineColor`, `bodyColor`, `secondaryColor`, `badge(bg, text)`, `button(bg, text)`, `cornerRadiusDp`, `strokeWidthDp`.
Every native spot has its own placement name: `intro_native` and `fullscreen_native` come from the slot methods, your own spots (language, home list) from
`.nativeAd("name").units(...)`. Several ids = floors, as everywhere. The view always shows the "Ad" badge, as the AdMob policy requires.

---

## Rules that apply to every format

- **Debug builds use Google test ads** automatically; release builds use your units.
- **Premium:** `GamechiefAds.setPremium(true)` hides every banner and native and stops every full-screen ad.
- **No ad available:** one pass over the floors, then a short message in the loading dialog (or nothing for banners and natives), and a wait of 5 s before the same spot asks again. No request loops.
- **Never overlapping:** one full-screen ad at a time, and 30 s of quiet for interstitials after an app open ad closes.
- **Change it without an app update:** units, floors, gaps, which slots are on, and more can be changed from the shared rules file or Remote Config ([USE_CASES](USE_CASES.md) recipe 21). Library code itself changes only with a new version.
