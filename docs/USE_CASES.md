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
            .splashAppOpenDoubleUnit(UNIT2)                    // the 2nd ad of a "double": ONE unit, no floors (same id as the first is fine)
            .splashInterstitialUnits(HF, MF, NORMAL)           // only if your splash shows an interstitial
            .splashInterstitialDoubleUnit(UNIT2)
            .splashBannerUnit(SPLASH_BANNER_UNIT)               // ONE unit, no floors

            // when the user comes back to the app: ONE unit, no floors
            .appOpenUnit(APP_OPEN_UNIT)

            // banners
            .bannerUnits(HF, MF, NORMAL)                       // all normal screens
            .exitBannerUnits(EXIT_BANNER_UNIT)                 // exit screen

            // random interstitial (automatic, plus your buttons), and the unit of its "double"
            .randomInterstitialUnits(HF, MF, NORMAL)           // automatic over activity transitions (+ buttons, recipe 6)
            .randomInterstitialDoubleUnit(UNIT2)

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

Double = two ads loaded at the same time and shown on top of each other. The first uses its floors; the second is one request to one unit (no floors; paste the same id if you like). The user closes the
first and the second is already there, so it looks like the same ad. No second unit given? The last unit of the first set is used.

No floors on the splash (one request only)? Add `.flooring(false)` after the units: it asks the **last** unit in the list.

### 3. A large banner on the splash

```java
GamechiefAds.loadSplashBannerAd(findViewById(R.id.splashBanner));   // any FrameLayout, always a 320x100 large banner, one unit, no floors
```

---

## Banners (give a FrameLayout, no XML ad view needed)

### 4. Banner on a normal screen, and on the exit screen

```java
GamechiefAds.loadBannerAd(findViewById(R.id.bannerContainer));          // with floors (HF, MF, NORMAL)
GamechiefAds.loadExitBannerAd(findViewById(R.id.exitContainer));
```

A shimmer shows while it loads (when the ui module is in your app). The container hides itself if there is no ad.
The banner type of a slot: `.bannerUnits(...).bannerType(BannerType.MEDIUM_RECTANGLE)`.

**Banner on a screen the user leaves fast?** A banner needs a moment to load, so on a screen where the user just taps a button and
moves on, the walk through the floors can be too slow and the banner never shows. Turn floors off for that call:

```java
GamechiefAds.loadBannerAd(findViewById(R.id.bannerContainer), false);       // isEnableFlooring = false
GamechiefAds.loadExitBannerAd(findViewById(R.id.exitContainer), false);
```

With `false` there is ONE request, to the **last** unit of the list (the lowest floor, the one that fills most often), so the banner
appears as fast as possible. `true` (the default) walks the floors. You choose per call, per screen. The splash banner never uses floors.

### 5. Full control: your own ad unit, size and type

```java
GamechiefAds.loadCustomBannerAd(frameLayout, "ca-app-pub-.../banner", BannerType.MEDIUM_RECTANGLE);
```

Types: `ADAPTIVE`, `COLLAPSIBLE_BOTTOM`, `COLLAPSIBLE_TOP`, `MEDIUM_RECTANGLE`, `LARGE_BANNER`, `BANNER`,
`INLINE_ADAPTIVE`, `FULL_BANNER`, `LEADERBOARD`. One unit, no floors, nothing needed in the config.
Add a listener as the last argument if you want to know when it loaded or failed.

---

## Random interstitial (automatic, plus buttons)

### 6. It shows by itself over activity transitions

You write **no code in your screens**. After you give the units in the config, the library watches your activities and, over a
**new** screen, shows an interstitial with the **"Ad is loading, please wait"** dialog:

```java
.randomInterstitialUnits(HF, MF, NORMAL)                // automatic: every 3rd new screen, at least 30 s after the last ad
.randomInterstitialUnits(HF, MF, NORMAL)
    .everyNScreens(2).minGapSeconds(45)                 // your pacing
    .excludeScreens(SplashActivity.class, PaywallActivity.class)   // never over these screens
```

- Counts only **new** screens (a freshly opened Activity). Back presses and rotation do not count and never show an ad.
- Never when another ad is open, for premium users, or over an excluded screen (put your splash there).
- The ad comes after the new screen has settled, so the transition is not cut. No ad available? The dialog says so for a moment and
  closes; during the short wait after that, new screens skip quietly (no repeated messages, no repeated requests).
- Two ads together on the automatic ad: add `.doubleAd(true)` (its second ad uses `randomInterstitialDoubleUnit(id)`).
- Turn the automatic part off (buttons only): `.everyNScreens(0)`.

### 6b. Also on your own buttons

```java
button.setOnClickListener(v -> GamechiefAds.loadRandomInterstitialAd(this, new InterstitialListener() {
    @Override public void onComplete(boolean wasShown) { openNextScreen(); }   // runs once, ad or no ad
}));
```

- The same ad, the same loading dialog, and the same minimum gap as the automatic one (they never show back to back).
- Not due yet (gap not over, not the Nth click, premium, another ad is open)? Nothing happens and `onComplete(false)` runs.
- Pace the clicks: `.everyNCalls(3)` shows on every 3rd call. Pass `null` if you do not need the listener.
- Two ads together: `GamechiefAds.loadRandomInterstitialDoubleAd(this, listener)`.

### 6c. Switch the automatic ad on and off while the app runs

```java
GamechiefAds.setAutoRandomInterstitialEnabled(false);   // e.g. in a purchase flow, a video, a game round
GamechiefAds.setAutoRandomInterstitialEnabled(true);    // back to normal
boolean on = GamechiefAds.isAutoRandomInterstitialEnabled();
```

- Off: no ad appears over new screens. Screens opened while it is off are **not counted**, so when you switch it on the count
  starts again from zero (the user does not get an ad on the very first screen after).
- Your button calls (`loadRandomInterstitialAd`) are not affected by the switch.
- It is on by default. Start with it off: `.randomInterstitialUnits(...).autoShow(false)` (buttons only).
- From Remote Config, no app update: `{"placements":{"random":{"auto":false}}}`.

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
    @Override public void onNotAvailable(String reason) { /* the dialog already told the user "No ad available" */ }
});
```

When no ad is available the dialog already shows "No ad available" (`onNotAvailable` is still called). Closing the ad early gives no reward. A premium user gets `onRewardEarned` with no ad (`.premiumGetsReward(false)` to stop that).

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

---

## Remote Config (built in)

### 15. Change the ad rules from the Firebase console, no code

Nothing to write. The library fetches Firebase Remote Config by itself and reads the key `gamechief_ads_config`
(change the name with `.remoteConfigKey("my_key")`). Put the JSON of recipe 12 in that key and publish. It is applied on the next
start and, with real-time updates, while the app runs.

You only need `google-services.json` and the google-services plugin once per app (see the README).

### 16. Your own values: add them, read them anywhere

```java
// 1. add them with a default, in the same config as the ad units
.remoteDefault("welcome_text", "Welcome")
.remoteDefault("show_promo", false)
.remoteDefault("free_coins", 10)
.remoteDefault("price_factor", 1.0)

// 2. read them in any Activity, any time
String text   = GamechiefAds.getRemoteString("welcome_text", "Welcome");
boolean promo = GamechiefAds.getRemoteBoolean("show_promo", false);
long coins    = GamechiefAds.getRemoteLong("free_coins", 10);
double factor = GamechiefAds.getRemoteDouble("price_factor", 1.0);
```

The second argument is the fallback if the key has no value anywhere. Use it to choose single or double ads:
`if (GamechiefAds.getRemoteBoolean("splash_double", false)) loadSplashAppOpenDoubleAd(...) else loadSplashAppOpenAd(...)`.

More: `GamechiefAds.addRemoteDefaults(map)` adds keys later, `GamechiefAds.fetchRemoteConfig(ok -> ...)` fetches now,
`GamechiefAds.setRemoteConfigListener(() -> ...)` runs after every update so you can re-read, `.remoteFetchIntervalSeconds(60)`
lowers the fetch interval while testing (default 3600).

---

## When no ad is available

### 17. What happens when every floor fails

Floors are **one pass**: high floor, then medium, then normal, each requested once. If the normal one fails too:

- the loading dialog turns into a short message (no spinner): **"No ad available, please try again in a few seconds"**, and closes after about 2 seconds,
- then your listener runs (`onNotShown` + `onComplete(false)`), so you open your next screen. The wait is short and bounded: the user is never stuck,
- **nothing is requested again by itself**. And if the user taps again within 5 seconds, no new requests are sent (the message
  shows again). After 5 seconds a tap starts one new pass.

```java
.noAdMessage("Ad nahi mila, thori der baad try karein")    // your own text or language (null = no message at all)
.noAdMessageMs(1500)                                          // how long the message stays in the dialog (default 1800)
.noFillRetrySeconds(10)                                      // wait before a new pass after "no ad" (default 5, 0 = no wait)
```

Remote Config: `{"noAdMessage":"...","noFillRetrySeconds":10}`. This applies to the random interstitial, your own custom
interstitials, rewarded ads and the app-open fetch. For rewarded ads you may show your own message in `onNotAvailable`
instead: then use `.noAdMessage(null)`.

---

## Ads never overlap, and interstitials stay quiet after an app open

### 18. What the library guarantees between interstitial and app open

- **One full-screen ad at a time.** While an app-open ad (splash or on return) is loading or on screen, no interstitial (automatic,
  button, custom) is requested or shown over it. While an interstitial is loading or on screen, no app-open ad is shown over it.
  The app open waits for its next chance.
- **30 seconds of quiet after an app open.** When the user closes an app-open ad, no interstitial appears for 30 seconds (counted
  from the moment it was **closed**, not from when it appeared). This covers the automatic random interstitial, the random
  interstitial on your buttons and your own custom interstitials. A button call during that time simply gets `onNotShown`
  (`DENY_AFTER_APP_OPEN`) and `onComplete(false)`, so your app carries on. A splash app-open also starts the quiet time.
- Rewarded ads are not held back: the user asked for them.

```java
.quietSecondsAfterAppOpen(45)        // your own time in seconds (default 30, 0 = no quiet time)
// Remote Config: {"quietSecondsAfterAppOpen": 45}
```
