package com.gcl.interiordesignai.Ads;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.gcl.interiordesignai.R;
import com.gcl.interiordesignai.Utils.SharedPreferenceManager;
import com.gcl.interiordesignai.databinding.LayoutInterstitialAdDialogBinding;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Map;
public class AppController extends Application {
    public SharedPreferenceManager sharedPreferences;
    public Context sContext;
    public static boolean isInAppPurchased = false;
    public static String IN_APP_PURCHASE_KEY = "is_in_app_purchased";
    public static String IN_APP_VALUE = "in_app_purchased";
    public static String isDebugging = "isDebugging";
    public static int adCounter = 0;
    public static int adShowInterval = 3;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseAnalytics.getInstance(this);
        sContext = getApplicationContext();

        sharedPreferences = new SharedPreferenceManager(sContext, IN_APP_VALUE);
        isInAppPurchased = sharedPreferences.getBooleanValue(IN_APP_PURCHASE_KEY, false);
//        isInAppPurchased = true;

        initFirebaseRemoteConfig(this);
        if (!isInAppPurchased) {
            MobileAds.initialize(sContext, initializationStatus -> {
                Map<String, AdapterStatus> statusMap = initializationStatus.getAdapterStatusMap();
                for (String adapterClass : statusMap.keySet()) {
                    AdapterStatus status = statusMap.get(adapterClass);
                    if (status != null) {
                        Log.d("MobileAds_initialize_", String.format(
                                "Adapter name: %s, Description: %s, Latency: %d",
                                adapterClass, status.getDescription(), status.getLatency()));
                    } else {
                        Log.e("MobileAds_initialize_", "null values");
                    }
                }
            });

//            InitializationConfig config = new InitializationConfig.Builder(getString(R.string.ADMOB_APP_ID)).build();
//            MobileAds.initialize(this, config, status -> Log.d("NextGenAds", "Initialized"));
            new AppOpenManager(AppController.this);
        }
    }

    public static void loadBigBannerAd(FrameLayout frameLayout) {
        if (!isInAppPurchased) {
            addShimmerInSmallBanner(frameLayout,false);
            AdSize adSize = AdSize.MEDIUM_RECTANGLE;
            AdRequest adRequest = new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
            loadHighFloorBanner(frameLayout, adRequest, adSize, 1);
        }
    }

    public static void loadLargeBannerAd(FrameLayout frameLayout) {
//        if (!isInAppPurchased) {
            addShimmerInSmallBanner(frameLayout,false);
            AdSize adSize = AdSize.LARGE_BANNER;
            AdRequest adRequest = new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
            loadNormalBanner(frameLayout, adRequest, adSize, 3);
//        }
    }
    public static void loadSplashBannerAd(Activity activity, FrameLayout frameLayout) {
        AdSize adSize ;
        if (splashBannerType == 2) adSize = AdSize.getLargePortraitAnchoredAdaptiveBannerAdSize(activity, ViewGroup.LayoutParams.MATCH_PARENT);
        else adSize = AdSize.LARGE_BANNER;
        AdRequest adRequest = new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
        loadNormalBanner(frameLayout, adRequest, adSize, 3);
    }

    public static void createAndGetAdaptiveAd(Activity activity, FrameLayout frameLayout) {
//        if (!isInAppPurchased) {
            addShimmerInSmallBanner(frameLayout,false);
            AdSize adSize = getAdSize(activity);
            AdRequest adRequest = new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
            loadHighFloorBanner(frameLayout, adRequest, adSize, 1);
//        }
    }

    public static void bannerAdForDrawingScreen(Activity activity, FrameLayout frameLayout) {
        if (!isInAppPurchased) {
            addShimmerInSmallBanner(frameLayout,false);
            AdSize adSize = getAdSize(activity);
            AdRequest adRequest = new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
            loadHighFloorBanner(frameLayout, adRequest, adSize, 2);
        }
    }

    private static void loadNormalBanner(FrameLayout frameLayout, AdRequest adRequest, AdSize adSize, int type) {
        AdView adView = new AdView(frameLayout.getContext());
        adView.setAdSize(adSize);

        switch (type){
            case 1:
                adView.setAdUnitId(frameLayout.getContext().getResources().getString(R.string.ADMOB_BANNER));
                break;
            case 2:
                adView.setAdUnitId(frameLayout.getContext().getResources().getString(R.string.ADMOB_BANNER));
                break;
            case 3:
                adView.setAdUnitId(frameLayout.getContext().getResources().getString(R.string.ADMOB_BANNER_SPLASH));
                break;
        }

        adView.loadAd(adRequest);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                adView.destroy();
                Log.e("775_bannerAd", loadAdError.getMessage());
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                frameLayout.removeAllViews();
                frameLayout.addView(adView);
                Log.d("775_bannerAd", "Normal floor banner loaded");
            }
        });
    }
    private static void loadHighFloorBanner(FrameLayout frameLayout, AdRequest adRequest, AdSize adSize, int type) {
        AdView adView = new AdView(frameLayout.getContext());
        adView.setAdSize(adSize);
        adView.setAdUnitId(frameLayout.getContext().getResources().getString(R.string.ADMOB_BANNER_HF));
        adView.loadAd(adRequest);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                adView.destroy();
                loadMediumFloorBanner(frameLayout, adRequest, adSize, type);
                Log.e("775_bannerAd", loadAdError.getMessage());
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                frameLayout.removeAllViews();
                frameLayout.addView(adView);
                Log.d("775_bannerAd", "High floor banner loaded");
            }
        });
    }
    private static void loadMediumFloorBanner(FrameLayout frameLayout, AdRequest adRequest, AdSize adSize,int type) {
        AdView adView = new AdView(frameLayout.getContext());
        adView.setAdSize(adSize);
        adView.setAdUnitId(frameLayout.getContext().getResources().getString(R.string.ADMOB_BANNER_MF));
        adView.loadAd(adRequest);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                adView.destroy();
                loadNormalBanner(frameLayout, adRequest, adSize, type);
                Log.e("775_bannerAd", loadAdError.getMessage());
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                frameLayout.removeAllViews();
                frameLayout.addView(adView);
                Log.d("775_bannerAd", "Medium floor banner loaded");
            }
        });
    }

    public static AdRequest getBannerAd(int type) {
        if (isShowAdaptiveBanner && !isInAppPurchased) {
            Bundle extras = new Bundle();
            if (type == 1) extras.putString("collapsible", "top");
            else extras.putString("collapsible", "bottom");
            return new AdRequest.Builder().setHttpTimeoutMillis(6000).addNetworkExtrasBundle(AdMobAdapter.class, extras).build();
        }else {
            return new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
        }
    }
    public static AdRequest getBannerAdReqForHome(int type) {
        if (isAdaptiveOnHomeEnable && !isInAppPurchased) {
            Bundle extras = new Bundle();
            if (type == 1) {
                extras.putString("collapsible", "top");
            } else {
                extras.putString("collapsible", "bottom");
            }
            return new AdRequest.Builder().setHttpTimeoutMillis(6000).addNetworkExtrasBundle(AdMobAdapter.class, extras).build();
        }else{
            return new AdRequest.Builder().setHttpTimeoutMillis(6000).build();
        }
    }

    private static AdSize getAdSize(Activity activity) {
        // Step 2 - Determine the screen width (less decorations) to use for the ad width.
        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        float widthPixels = outMetrics.widthPixels;
        float density = outMetrics.density;
        int adWidth = (int) (widthPixels / density);
        // Step 3 - Get adaptive ad size and return for setting on the ad view.
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }
    public static boolean isIsShowingInterstitialAd = false;

    public static void showInterstitialAdWithLoader(Activity activity, MyInterstitialAdListener adListener) {
        if (!isInAppPurchased) {
            LayoutInterstitialAdDialogBinding dialogBinding = LayoutInterstitialAdDialogBinding.inflate(activity.getLayoutInflater());
            Dialog dialog = new Dialog(activity);
            dialog.setContentView(dialogBinding.getRoot());
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dialog.show();

            FullScreenContentCallback listener2 = new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent();
                    isIsShowingInterstitialAd = false;
                    adListener.onAdClosed();
                }
                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    super.onAdFailedToShowFullScreenContent(adError);
                    isIsShowingInterstitialAd = false;
                    adListener.onFailedToLoad(adError.getMessage());
                }
            };

            AdRequest adRequest = new AdRequest.Builder().setHttpTimeoutMillis(6000).build();

            if (isShowDoubleInterstitialAdEnabled()) {
                InterstitialAd.load(activity, activity.getString(R.string.ADMOB_INTERSTITIAL_DOUBLE), adRequest,
                        new InterstitialAdLoadCallback() {
                            @Override
                            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                                Log.i("779_interNormal_", "onAdLoaded");
                                interstitialAd.setFullScreenContentCallback(listener2);
                                dialog.dismiss();
                                interstitialAd.show(activity);
                            }

                            @Override
                            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                                Log.i("779_interNormal_", loadAdError.getMessage());
                                adListener.onFailedToLoad(loadAdError.getMessage());
                                dialog.dismiss();
                                Toast.makeText(activity, "No Ad Available", Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            InterstitialAd.load(activity, activity.getString(R.string.ADMOB_INTERSTITIAL_HF),
                    adRequest, new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                            Log.d("779_interHighFloor_", "onAdLoaded");
                            interstitialAd.setFullScreenContentCallback(listener2);
                            isIsShowingInterstitialAd = true;
                            dialog.dismiss();
                            interstitialAd.show(activity);
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            Log.e("779_interHighFloor_", loadAdError.getMessage());
                            InterstitialAd.load(activity, activity.getString(R.string.ADMOB_INTERSTITIAL_MF), adRequest,
                                    new InterstitialAdLoadCallback() {
                                        @Override
                                        public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                                            Log.i("779_interMediumFloor_", "onAdLoaded");
                                            interstitialAd.setFullScreenContentCallback(listener2);
                                            isIsShowingInterstitialAd = true;
                                            dialog.dismiss();
                                            interstitialAd.show(activity);
                                        }

                                        @Override
                                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                                            Log.i("779_interMediumFloor_", loadAdError.getMessage());
                                            InterstitialAd.load(activity, activity.getString(R.string.ADMOB_INTERSTITIAL), adRequest,
                                                    new InterstitialAdLoadCallback() {
                                                        @Override
                                                        public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                                                            Log.i("779_interNormal_", "onAdLoaded");
                                                            adListener.onFailedToLoad(loadAdError.getMessage());
                                                            interstitialAd.setFullScreenContentCallback(listener2);
                                                            dialog.dismiss();
                                                            interstitialAd.show(activity);
                                                        }

                                                        @Override
                                                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                                                            Log.i("779_interNormal_", loadAdError.getMessage());
                                                            dialog.dismiss();
                                                            Toast.makeText(activity, "No Ad Available", Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
                                        }
                                    });
                        }
                    });
        }
    }

    public static void createUnifiedAds(Context context, int unitId,AdRequest request, AdUnifiedListening listening) {
        AdLoader.Builder builder = new AdLoader.Builder(context,context.getString(unitId));
//        AdLoader.Builder builder = new AdLoader.Builder(context,"ca-app-pub-3940256099942544/2247696110");
        builder.forNativeAd(listening);
        builder.withAdListener(listening);
        AdLoader adLoader = builder.build();
        adLoader.loadAds(request,1);
        listening.setAdLoader(adLoader);
    }

    public static void addShimmerInSmallBanner(FrameLayout frameLayout, boolean isBig){
        if (frameLayout!=null && frameLayout.getContext()!=null) {
            LottieAnimationView lottieAnimationView = new LottieAnimationView(frameLayout.getContext());
            lottieAnimationView.setBackgroundColor(frameLayout.getContext().getResources().getColor(R.color.white));
            if (isBig) lottieAnimationView.setAnimation(R.raw.shimmer_big);
            else lottieAnimationView.setAnimation(R.raw.shimmer_banner_sm);
            lottieAnimationView.setSpeed(1f);
            lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);
            frameLayout.addView(lottieAnimationView);
            lottieAnimationView.playAnimation();
        }
    }
    public static LottieAnimationView addShimmerInSmallBanner(FrameLayout frameLayout){
        LottieAnimationView lottieAnimationView = null;
        if (frameLayout!=null && frameLayout.getContext()!=null) {
            lottieAnimationView = new LottieAnimationView(frameLayout.getContext());
            lottieAnimationView.setBackgroundColor(frameLayout.getContext().getResources().getColor(R.color.white));
            lottieAnimationView.setAnimation(R.raw.shimmer_banner_sm);
            lottieAnimationView.setSpeed(1f);
            lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);
            frameLayout.addView(lottieAnimationView);
            lottieAnimationView.playAnimation();
        }
        return lottieAnimationView;
    }
    /*__________________________________________________________________________________*/
    /*______________________________Remote Config____________________________________________________*/
    public static boolean isShowAdOnInAppClose = false;
    public static boolean isBigNativeIntroEnable = false;
    public static boolean isAdaptiveOnHomeEnable = false;
    public static boolean isShowAdaptiveBanner = false;
    public static boolean isShowDoubleInterstitialAd = false;
    public static boolean showInterstitialOnBottomNaviClicks = false;
    public static boolean isFirebaseEnabled = true;
    public static boolean isSplashBannerAdEnable = true;
    public static boolean showDoubleSplashAppOpenAd = false;
    public static boolean isShowIntroEveryTime = false;
    public static boolean showInterstitialAdOnGenerateBtn = false;
    public static boolean isFreeTrialEnable = false;
    public static boolean enableReward = true;
    public static int noOfBigNativeAdsIntroScreen = 2;
    public static int splashBannerType = 2; // 1 for large Banner, 2 for large anchor

    private void initFirebaseRemoteConfig(Context sContext) {
        String TAG = "mFirebaseRemoteConfig_1";
        FirebaseApp.initializeApp(sContext);
        FirebaseRemoteConfig mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(5000).build();

        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);
        mFirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);
        mFirebaseRemoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean updated = task.getResult();
                isFirebaseEnabled = mFirebaseRemoteConfig.getBoolean("firebaseEnabled");
                if (isFirebaseEnabled) {
                    if (!mFirebaseRemoteConfig.getString("adShowInterval").isEmpty())
                        adShowInterval = Integer.parseInt(mFirebaseRemoteConfig.getString("adShowInterval"));
                    if (!mFirebaseRemoteConfig.getString("noOfBigNativeAdsIntroScreen").isEmpty())
                        noOfBigNativeAdsIntroScreen = Integer.parseInt(mFirebaseRemoteConfig.getString("noOfBigNativeAdsIntroScreen"));

                    isShowAdOnInAppClose = mFirebaseRemoteConfig.getBoolean("isShowAdOnInAppClose");
                    showInterstitialAdOnGenerateBtn = mFirebaseRemoteConfig.getBoolean("showInterstitialAdOnGenerateBtn");
                    isBigNativeIntroEnable = mFirebaseRemoteConfig.getBoolean("isBigNativeIntroEnable");
                    isShowAdaptiveBanner = mFirebaseRemoteConfig.getBoolean("isShowAdaptiveBanner");
                    showDoubleSplashAppOpenAd = mFirebaseRemoteConfig.getBoolean("showDoubleSplashAppOpenAd");
                    isShowIntroEveryTime = mFirebaseRemoteConfig.getBoolean("isShowIntroEveryTime");
                    showInterstitialOnBottomNaviClicks = mFirebaseRemoteConfig.getBoolean("showInterstitialOnBottomNaviClicks");
                    enableReward = mFirebaseRemoteConfig.getBoolean("enableReward");

                    Log.d(TAG, "Config params updated: " +
                            updated + " - "  + " - " + showInterstitialOnBottomNaviClicks + " - " + adShowInterval);
                    Log.d(TAG, "updated_ad_units: " + " - "
                    );
                }


            } else {
                Log.d(TAG, "Config Fetching data failed: " + " - " + adShowInterval);
            }
        });
    }

    public static boolean isDoubleAppOpenOnSplashEnabled() {
        return showDoubleSplashAppOpenAd = FirebaseRemoteConfig.getInstance().getBoolean("showDoubleSplashAppOpenAd");
//        return true;
    }
    public static boolean isShowIntroScreenEveryTime() {
        return isShowIntroEveryTime = FirebaseRemoteConfig.getInstance().getBoolean("isShowIntroEveryTime");
    }
    public static boolean isBigNativeIntroScreenEnabled() {
        return isBigNativeIntroEnable = FirebaseRemoteConfig.getInstance().getBoolean("isBigNativeIntroEnable");
//        return true;
    }
    public static boolean isShowDoubleInterstitialAdEnabled() {
        return isShowDoubleInterstitialAd = FirebaseRemoteConfig.getInstance().getBoolean("isShowDoubleInterstitialAd");
//        return true;
    }
    public static boolean showInterstitialOnBottomNaviClicksEnabled() {
        return showInterstitialOnBottomNaviClicks = FirebaseRemoteConfig.getInstance().getBoolean("showInterstitialOnBottomNaviClicks");
//        return true;
    }
    public static boolean isFreeTrialEnabled() {
        return isFreeTrialEnable = FirebaseRemoteConfig.getInstance().getBoolean("isFreeTrialEnable");
//        return true;
    }
    public static boolean isEnableReward() {
        return enableReward = FirebaseRemoteConfig.getInstance().getBoolean("enableReward");
    }
    public static int noOfNativeBetweenIntroSlider() {
        return noOfBigNativeAdsIntroScreen = Integer.parseInt(FirebaseRemoteConfig.getInstance().getString("noOfBigNativeAdsIntroScreen"));
    }



}
