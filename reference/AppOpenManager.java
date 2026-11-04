package com.gcl.interiordesignai.Ads;
import static com.gcl.interiordesignai.Ads.AppController.isInAppPurchased;
import static com.gcl.interiordesignai.Ads.AppController.isIsShowingInterstitialAd;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.gcl.interiordesignai.Activities.InternetCheckActivity;
import com.gcl.interiordesignai.Activities.SplashActivity;
import com.gcl.interiordesignai.R;
import com.gcl.interiordesignai.Utils.NetworkUtils;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

import java.util.Date;
public class AppOpenManager implements LifecycleObserver, Application.ActivityLifecycleCallbacks {
    private final AppController myApplication;
    private AppOpenAd appOpenAd = null;
    private Activity currentActivity;
    public static boolean isShowingAd = false;
    private static boolean isAdLoaded;
    private long loadTime = 0;
    public static boolean isInternetConnected = false;
    public static boolean isAllowedAppOpen = true;

    public AppOpenManager(AppController myApplication) {
        this.myApplication = myApplication;
        this.myApplication.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
//        myApplication.registerReceiver(networkChangeReceiver, new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            myApplication.registerReceiver(networkChangeReceiver, new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION), Context.RECEIVER_NOT_EXPORTED);
        } else {
            myApplication.registerReceiver(networkChangeReceiver, new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            InternetCheckActivity netCheckActivity = new InternetCheckActivity();
            String status = NetworkUtils.getConnectivityStatusStringAS(context);
            if (!status.equals("Not connected to Internet")) {
                isInternetConnected = true;
                if ((currentActivity instanceof InternetCheckActivity))
                    netCheckActivity.internetConnected(currentActivity);
            }
            else{
                if (!(currentActivity instanceof InternetCheckActivity)) {
                    context.startActivity(new Intent(context, netCheckActivity.getClass())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                Toast.makeText(context, "Internet Disconnected", Toast.LENGTH_SHORT).show();
            }
        }
    };

    /** LifecycleObserver methods */
//    @OnLifecycleEvent(ON_START)
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onActivityResumed() {
        if (!(currentActivity instanceof SplashActivity)) {
            if (!isInAppPurchased&& !isIsShowingInterstitialAd){
                if (isAllowedAppOpen) showAdIfAvailable();
                else isAllowedAppOpen = true;
            }
        }
    }

    public void showAdIfAvailable() {
        if (!isShowingAd && isAdAvailable() && appOpenAd!=null) {
            Log.d("771_AppOpenManager__", "Will show ad.");
            FullScreenContentCallback fullScreenContentCallback = new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    AppOpenManager.this.appOpenAd = null;
                    isShowingAd = false;
                    AppController.adCounter=0;
                    fetchAd();
                }
                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isShowingAd = false;
                    AppOpenManager.this.appOpenAd = null;
                    Log.d("771_AppOpenManager__", "Failed : "+adError.getMessage());
                }
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingAd = true;
                    AppOpenManager.this.appOpenAd = null;
                }
            };
            if (!isInAppPurchased && appOpenAd!=null) {
                appOpenAd.setFullScreenContentCallback(fullScreenContentCallback);
                appOpenAd.show(currentActivity);
            }
        }
        else {
            Log.d("771_AppOpenManager__", "Can not show ad.");
            fetchAd();
        }
    }

    public void fetchAd() {
        if (isAdAvailable()) return;
        AdRequest request = new AdRequest.Builder().setHttpTimeoutMillis(5000).build();
        fetchNormalAd(request);
    }

    public void fetchNormalAd(AdRequest adRequest) {
        AppOpenAd.load(myApplication, myApplication.getString(R.string.ADMOB_OPEN_APP), adRequest,
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd appOpenAd) {
                        super.onAdLoaded(appOpenAd);
                        isAdLoaded = true;
                        AppOpenManager.this.appOpenAd = appOpenAd;
                        AppOpenManager.this.loadTime = (new Date()).getTime();
                        Log.d("771_fetchAdNormalFloor","Succeed Normal ad loaded");
                    }
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        isAdLoaded = false;
                        AppOpenManager.this.appOpenAd = null;
                        Log.e("771_fetchAdNormalFloor","Failed: "+loadAdError.getMessage());
                    }
                });
    }

    /** Utility method to check if ad was loaded more than n hours ago. */
    private boolean wasLoadTimeLessThanNHoursAgo(long numHours) {
        long dateDifference = (new Date()).getTime() - this.loadTime;
        long numMilliSecondsPerHour = 3600000;
        return (dateDifference < (numMilliSecondsPerHour * numHours));
    }
    /**
     * Utility method that checks if ad exists and can be shown.
     */
    public boolean isAdAvailable() {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
//        currentActivity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        isShowingAd = false;
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) { }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) { currentActivity = null; }

}