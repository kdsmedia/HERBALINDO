package com.altomedia.herbalindo.ads;

import android.content.Context;

import com.altomedia.herbalindo.core.Config;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.LoadAdError;
import androidx.annotation.NonNull;

/**
 * Pengelola iklan AdMob: banner dan rewarded.
 * Callback rewarded hanya dipanggil setelah pengguna benar-benar menonton
 * sampai selesai (onUserEarnedReward), sehingga poin tidak dapat diklaim
 * tanpa menonton iklan.
 */
public class AdsManager {

    public interface RewardCallback {
        void onRewarded();
        void onFailed(String reason);
    }

    private static AdsManager instance;
    private RewardedAd rewardedAd;
    private boolean loading;
    private boolean initialized;

    public static synchronized AdsManager get(Context ctx) {
        if (instance == null) instance = new AdsManager();
        return instance;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        MobileAds.initialize(com.altomedia.herbalindo.HerbalindoApp.get(), initializationStatus -> { });
    }

    public void loadBanner(AdView view) {
        if (view == null) return;
        AdRequest req = new AdRequest.Builder().build();
        view.loadAd(req);
    }

    public void preloadRewarded() {
        if (rewardedAd != null || loading) return;
        loading = true;
        RewardedAd.load(com.altomedia.herbalindo.HerbalindoApp.get(), Config.ADMOB_REWARDED_UNIT,
                new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        loading = false;
                    }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError err) {
                        rewardedAd = null;
                        loading = false;
                    }
                });
    }

    public boolean isRewardedReady() { return rewardedAd != null; }

    public void showRewarded(android.app.Activity activity, RewardCallback cb) {
        if (rewardedAd == null) {
            preloadRewarded();
            cb.onFailed("Iklan belum siap. Coba lagi sebentar.");
            return;
        }
        final boolean[] earned = {false};
        rewardedAd.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                rewardedAd = null;
                preloadRewarded();
                if (!earned[0]) cb.onFailed("Iklan ditutup sebelum selesai. Poin tidak diberikan.");
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                rewardedAd = null;
                preloadRewarded();
                cb.onFailed("Iklan gagal ditampilkan: " + adError.getMessage());
            }
        });
        rewardedAd.show(activity, rewardItem -> {
            earned[0] = true;
            cb.onRewarded();
        });
    }
}