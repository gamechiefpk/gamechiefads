# the core module creates these ui classes by name
-keep class com.gcl.gamechiefads.ui.DefaultLoaderDialog { public <init>(java.lang.CharSequence); }
-keep class com.gcl.gamechiefads.ui.ShimmerBannerPlaceholder { public <init>(); }
-keep class com.gcl.gamechiefads.ui.AdsUi { public static void loadNativeAd(android.widget.FrameLayout, java.lang.String, java.lang.String); }
-keep class com.gcl.gamechiefads.ui.DebugSheet { public static void show(android.app.Activity); }
-keep class com.gcl.gamechiefads.ui.QaActivity
