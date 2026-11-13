package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.GamechiefAds;

import java.util.HashMap;
import java.util.Map;

/** Your own Remote Config values: add them in the config with remoteDefault(...), read them anywhere. */
public class RemoteConfigActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Remote Config";
    }

    @Override
    protected void build() {
        note("Remote Config is built in. Here a pretend server is used (the sample has no Firebase project). "
                + "Values were added in SampleApp with remoteDefault(...).");
        button("Read my values", this::readValues);
        button("Server changes welcome_text + free_coins", () -> {
            DemoRemoteSource.INSTANCE.publish("welcome_text", "Hello from the server");
            DemoRemoteSource.INSTANCE.publish("free_coins", 50L);
            say("server: welcome_text and free_coins changed");
        });
        button("Server changes the ADS rules (random: every call, no gap)", () -> {
            DemoRemoteSource.INSTANCE.publish(com.gcl.gamechiefads.AdsConfig.DEFAULT_REMOTE_KEY,
                    "{\"placements\":{\"random\":{\"everyNCalls\":1,\"gap\":0}}}");
            say("server: ads rules changed. Open \"Random click interstitial\" and click: every click now asks for an ad");
        });
        button("Add a new default at runtime", () -> {
            Map<String, Object> m = new HashMap<>();
            m.put("runtime_key", "added in code");
            GamechiefAds.addRemoteDefaults(m);
            say("runtime_key = " + GamechiefAds.getRemoteString("runtime_key", "?"));
        });
        button("Fetch now", () -> GamechiefAds.fetchRemoteConfig(ok -> say("fetch finished, success=" + ok)));
        GamechiefAds.setRemoteConfigListener(() -> say("listener: values updated, re-read them here"));
        readValues();
    }

    private void readValues() {
        say("available=" + GamechiefAds.isRemoteConfigAvailable()
                + " | welcome_text=" + GamechiefAds.getRemoteString("welcome_text", "?")
                + " | show_promo=" + GamechiefAds.getRemoteBoolean("show_promo", false)
                + " | free_coins=" + GamechiefAds.getRemoteLong("free_coins", 0)
                + " | price_factor=" + GamechiefAds.getRemoteDouble("price_factor", 1.0)
                + " | missing=" + GamechiefAds.getRemoteString("not_a_key", "my fallback"));
    }

    @Override
    protected void onDestroy() {
        GamechiefAds.setRemoteConfigListener(null);
        super.onDestroy();
    }
}
