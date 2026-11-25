# Starter: copy, paste, replace the ids

A small complete flow (splash, language, intro, home with the exit dialog) using GamechiefAds. Copy the files into your app, change the package name,
put your own ad unit ids in `MyApp.java`, and it runs. Debug builds show Google test ads by themselves.

| File | What it shows |
|---|---|
| [`MyApp.java`](MyApp.java) | All ad units in one place (`GamechiefAds.init`) |
| [`SplashActivity.java`](SplashActivity.java) | Splash banner + one full-screen ad, then the next screen |
| [`LanguageActivity.java`](LanguageActivity.java) | Small-media native on the language screen (one line) |
| [`IntroActivity.java`](IntroActivity.java) | The intro slider with `IntroPager`: only page layouts, ads and "Get started" built in |
| [`HomeActivity.java`](HomeActivity.java) | Banner with no code (a tag), random click ad, rewarded ad, exit dialog banner |
| [`res/layout/`](res/layout) | The layouts: ads are just empty `FrameLayout`s |
| [`AndroidManifest.snippet.xml`](AndroidManifest.snippet.xml) | The app id and the activities |

## The steps

1. **Dependency** (`build.gradle`, see the main README for the exact lines): the one JitPack line, and the global exclude of `play-services-ads`.
2. **Manifest:** your AdMob app id in `<meta-data>`, and `android:name=".MyApp"` on `<application>` (`AndroidManifest.snippet.xml`).
3. **`MyApp.java`:** your ad units. Delete the lines of the ads you do not use.
4. **Screens:** copy the activities and layouts you need. The ad parts are the lines under the "ad" comments, one line each.
5. **Run the app.** Look at logcat (tag `GamechiefAds`): at start it prints a setup check that tells you what is missing or wrong, in plain words.

## Good to know

- **Banner with no code:** `android:tag="gads_banner"` on a `FrameLayout`. Also `gads_banner_fast` (no floors), `gads_exit_banner`, `gads_native:<placement>[:<size>]`.
- **Double ads, the full-screen native and big native sizes are OFF.** The owner turns them on in Firebase Remote Config (`docs/firebase/`). Nothing to do in code.
- **Premium:** `GamechiefAds.setPremium(true)` hides every ad.
- **Testing in a release build:** `GamechiefAds.setTestMode(true)` makes every ad use Google test ids; `GamechiefAds.openAdInspector()` opens Google's Ad Inspector;
  `GamechiefAds.checkSetup()` returns the setup check as a list. Put them behind a QA menu.
- All ad types, sizes and the full guide: [AD_FORMATS.md](../AD_FORMATS.md), [USE_CASES.md](../USE_CASES.md).
