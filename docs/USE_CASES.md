# GamechiefAds: how do I...?

Copy-paste recipes. Two steps for everything:

1. **Once**, in your `Application`: give all ad units to `GamechiefAds.init(...)`.
2. **In a screen**: call one method named `load...Ad`.

In debug builds you see Google test ads and a trace in logcat (`adb logcat -s GamechiefAds`).
Every call is safe: no ad is just "no ad", never a crash or a stuck screen.

---

## 1. Put all your ad units in one place (Application)

```java
public class MyApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        GamechiefAds.init(this, AdsConfig.builder()
            .appId("ca-app-pub-XXXX~YYYY")

            // splash (several ids = floors: the first is tried first, then the next, then the next)
            .splashAppOpenUnits(HF, MF, NORMAL)
            .splashAppOpenDoubleUnits(HF2, MF2, NORMAL2)      // units of the 2nd ad of a "double"
            .splashInterstitialUnits(HF, MF, NORMAL)           // only if your splash shows an interstitial
            .splashInterstitialDoubleUnits(HF2, MF2, NORMAL2)
            .splashBannerUnits(HF, MF, NORMAL)

            // when the user comes back to the app: ONE unit, no floors
            .appOpenUnit(APP_OPEN_UNIT)

            // banners
            .bannerUnits(HF, MF, NORMAL)                       // all normal screens
            .exitBannerUnits(EXIT_BANNER_UNIT)                 // exit screen

            // interstitial on random clicks, and the units of its "double"
            .randomInterstitialUnits(HF, MF, NORMAL).minGapSeconds(30).everyNCalls(3)
            .randomInterstitialDoubleUnits(HF2, MF2, NORMAL2)

            // native
            .introNativeUnits(HF, MF, NORMAL)                  // small native, intro screens, floors
            .fullScreenNativeUnit(FULL_NATIVE_UNIT)            // full-screen native, ONE unit, no floors

            .rewardedUnits(HF, MF, NORMAL)
            .build());
    }
}
```

You only write the lines you use. Ad units come from your AdMob account (or `getString(R.string.xxx)`).
`GamechiefAds` is a static object: call it from any Activity, nothing to keep or pass around.

---

## Splash

### 2. Splash ad, then open the app

```java
GamechiefAds.loadSplashAppOpenAd(this, ads ->
    ads.show(this, () -> startActivity(new Intent(this, MainActivity.class))));
```

`ads.show` shows the ad and calls your code **once** when it is closed. If there is no ad (offline, premium, no fill, timeout)
it calls your code straight away. Open your next screen there, always.

| I want on the splash | Call |
|---|---|
| App open ad | `loadSplashAppOpenAd(this, callback)` |
| Two app open ads together (double) | `loadSplashAppOpenDoubleAd(this, callback)` |
| Interstitial | `loadSplashInterstitialAd(this, callback)` |
| Two interstitials together (double) | `loadSplashInterstitialDoubleAd(this, callback)` |

Double = two ads loaded at the same time, from two separate sets of units, shown on top of each other. The user closes the
first and the second is already there, so it looks like the same ad. No second units given? The first units are used twice.

No floors on the splash (one request only)? Add `.flooring(false)` after the units: it asks the **last** unit in the list.

### 3. A large banner on the splash

```java
GamechiefAds.loadSplashBannerAd(findViewById(R.id.splashBanner));   // any FrameLayout, always a 320x100 large banner
```

---

## Banners (give a FrameLayout, no XML ad view needed)

### 4. Banner on a normal screen, and on the exit screen

```java
GamechiefAds.loadBannerAd(findViewById(R.id.bannerContainer));
GamechiefAds.loadExitBannerAd(findViewById(R.id.exitContainer));
```

A shimmer shows while it loads (when the ui module is in your app). The container hides itself if there is no ad.
The banner type of a slot: `.bannerUnits(...).bannerType(BannerType.MEDIUM_RECTANGLE)`.

### 5. Full control: your own ad unit, size and type

```java
GamechiefAds.loadCustomBannerAd(frameLayout, "ca-app-pub-.../banner", BannerType.MEDIUM_RECTANGLE);
```

Types: `ADAPTIVE`, `COLLAPSIBLE_BOTTOM`, `COLLAPSIBLE_TOP`, `MEDIUM_RECTANGLE`, `LARGE_BANNER`, `BANNER`,
`INLINE_ADAPTIVE`, `FULL_BANNER`, `LEADERBOARD`. One unit, no floors, nothing needed in the config.
Add a listener as the last argument if you want to know when it loaded or failed.

---

## Interstitial on random clicks

### 6. Call it on a click, the library decides

```java
button.setOnClickListener(v -> GamechiefAds.loadRandomInterstitialAd(this, new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { openNextScreen(); }   // runs once, ad or no ad
}));
```

- Not due yet (gap not over, not the Nth click, premium, another ad is open)? Nothing happens and `onComplete(false)` runs.
- Due: the **"Ad is loading, please wait"** dialog shows while the ad loads. This dialog is always shown (it is built in).
- Pass `null` if you do not need the listener.
- Pace it in the config: `.randomInterstitialUnits(...).minGapSeconds(30).everyNCalls(3)`
  (at least 30 s since the last ad, and only on every 3rd click).
- Two ads together: `GamechiefAds.loadRandomInterstitialDoubleAd(this, listener)`.

### 7. Change the loading dialog text

```java
GamechiefAds.setLoadingText("Ad is loading", "Please wait...");    // any time, e.g. after a language change
```

Or from Remote Config: `{"loaderTitle":"Ad is loading","loaderMessage":"Please wait..."}`.
Own look: `GamechiefAds.setLoadingDialog(DefaultLoaderDialog.builder().accentColor(0xFF6C4CF1).build())`.

---

## Native (needs the ui module, in `AdsUi`)

### 8. Small native on the intro screens, full-screen native like an interstitial

```java
AdsUi.loadIntroNativeAd(findViewById(R.id.nativeContainer));     // small, bottom of the screen, with floors

AdsUi.loadFullScreenNativeAd(this, new InterstitialListener() {  // covers the screen, close button after a few seconds
    @Override public void onComplete(boolean wasShown) { openNextScreen(); }
});
```

---

## Rewarded

### 9. Watch an ad to get coins

```java
GamechiefAds.loadRewardedAd(this, new RewardListener() {
    @Override public void onRewardEarned(int amount, String type) { addCoins(10); }       // give the reward ONLY here
    @Override public void onNotAvailable(String reason) { toast("No ad available, try again later"); }
});
```

Closing the ad early gives no reward. A premium user gets `onRewardEarned` with no ad (`.premiumGetsReward(false)` to stop that).

---

## Other

### 10. Premium users see no ads

```java
GamechiefAds.setPremium(isPremium);     // from your billing library's callback
```

### 11. Skip the app-open ad once (billing, camera, permissions)

```java
GamechiefAds.skipNextAppOpenAd();
```

### 12. Change rules without an app update (Remote Config)

```java
GamechiefAds.applyRemoteConfig(remoteConfig.getString("ads_config"));
```

```json
{"placements": {"random":  {"everyNCalls": 5, "gap": 60},
                "banner":  {"enabled": false},
                "splash":  {"flooring": false, "units": ["id1", "id2"]}}}
```

The slot names are `splash`, `splash_interstitial`, `splash_banner`, `app_open`, `banner`, `exit_banner`, `random`,
`intro_native`, `fullscreen_native`, `rewarded`. Bad JSON is ignored. `{"enabled": false}` turns every ad off.
Choosing single or double is your call: call the normal or the `...Double...` method (for example from a Remote Config flag).

### 13. Why did no ad show?

```bash
adb logcat -s GamechiefAds
```

`[random]` says why a click did not show (`DENY_MIN_GAP`, `DENY_EVERY_N`, `DENY_PREMIUM`...), `[gate]` is consent or SDK init,
`[pipeline]` and `[sdk]` show each ad unit and its AdMob error. Release builds print nothing.

### 14. Mediation (Pangle, Unity, Meta, Mintegral, AppLovin)

Nothing to code: add the partners to your ad units in the AdMob console and use those unit ids. Setup lines are in the README.
