package com.gcl.gamechiefads.sample;

import android.widget.TextView;
import android.widget.Toast;

import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.RewardListener;

/** Rewarded ad: the coins are added ONLY in onRewardEarned. Closing the ad early gives nothing. */
public class RewardedActivity extends DemoActivity {
    private int coins;
    private TextView coinView;

    @Override
    protected String screenTitle() {
        return "Rewarded ad";
    }

    @Override
    protected void build() {
        coinView = new TextView(this);
        coinView.setTextSize(24);
        content.addView(coinView);
        showCoins();
        note("Tap, watch the ad to the end and you get 10 coins. Close it early: no coins. Premium (hub): coins with no ad.");
        button("Watch an ad for 10 coins", () ->
                GamechiefAds.showRewarded(this, AdIds.REWARDED, new RewardListener() {
                    @Override
                    public void onRewardEarned(int amount, String type) {
                        coins += 10; // give the reward here, and only here
                        showCoins();
                        say("reward earned: " + amount + " " + type);
                    }

                    @Override
                    public void onNotAvailable(String reason) {
                        say("no ad: " + reason);
                        Toast.makeText(RewardedActivity.this, "No ad available, try again later", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete(boolean rewarded) {
                        say("complete, rewarded=" + rewarded);
                    }
                }));
        button("Double tap: two calls at once", () -> {
            say("calling twice; the second must be refused");
            GamechiefAds.showRewarded(this, AdIds.REWARDED, null);
            GamechiefAds.showRewarded(this, AdIds.REWARDED, new RewardListener() {
                @Override
                public void onNotAvailable(String reason) {
                    say("second call refused: " + reason);
                }
            });
        });
    }

    private void showCoins() {
        coinView.setText("Coins: " + coins);
    }
}
