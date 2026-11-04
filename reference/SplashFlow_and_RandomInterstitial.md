# Reference: owner's current splash ads flow (Java) and random interstitial rule

## Splash (SplashActivity)
- State flags: isAnimationFinished (9 s animation timer), isAdResolved (ad loaded OR failed OR consent denied OR premium), isButtonShown.
- MAX_WAIT 12 s hard timeout sets isAdResolved=true. Start button appears only when animation finished AND ad resolved (tryShowStartButton).
- If not premium: UMP consent (ConsentRequestParameters, requestConsentInfoUpdate, loadAndShowConsentFormIfRequired);
  if canRequestAds -> load splash banner (if remote flag isSplashBannerAdEnable) + app-open waterfall; else isAdResolved=true.
- App-open waterfall, nested callbacks: unit HF -> MF -> NORMAL, request with setHttpTimeoutMillis(5000). On loaded: keep ad, set
  FullScreenContentCallback (dismissed/failed-to-show -> moveToNextActivity), isAdResolved=true.
- Optional second ("double") app-open ad loaded in parallel when remote flag showDoubleSplashAppOpenAd is on (unit ADMOB_OPEN_APP_DOUBLE), no failure handling.
- Start button click: if not premium -> show loadedAppOpenAd (else moveToNextActivity); then, if double enabled and ad2 loaded, show ad2 immediately
  (BUG: shown right after the first, and navigation can happen twice). Premium -> moveToNextActivity.
- moveToNextActivity: intro screen on first launch or if remote flag isShowIntroEveryTime, else HomeActivity; finish().

## Random interstitial (called today in EVERY activity onCreate)
    if (adCounter > 0 && adCounter % adShowInterval == 0) showInterstitialAdWithLoader(activity, null);
- adCounter is a static int, adShowInterval remote-config (default 3). adCounter is reset to 0 in AppOpenManager when an app-open ad is dismissed.
- showInterstitialAdWithLoader(activity, listener): shows a full-screen loader dialog, loads HF -> MF -> NORMAL (nested), optional double interstitial
  (loads a second unit in parallel, BUG: both can show), listener may be null (BUG: NPE in onAdClosed / onFailedToShow when null).
- Goal: library hooks Activity lifecycle itself so no per-activity call is needed; rule becomes every-N-screens + min gap + excluded screens.
