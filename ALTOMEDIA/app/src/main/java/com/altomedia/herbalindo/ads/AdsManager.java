package com.altomedia.herbalindo.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.altomedia.herbalindo.core.Config;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Pengelola iklan AdMob: banner, rewarded, dan interstitial.
 *
 * Poin hanya diberikan setelah pengguna benar-benar menonton rewarded sampai
 * selesai (onUserEarnedReward). Iklan yang ditutup lebih awal atau gagal tayang
 * tidak memberi poin, sehingga saldo tidak dapat diklaim tanpa menonton iklan.
 */
public class AdsManager {

    private static final String TAG = "AdsManager";

    private static AdsManager instance;

    private RewardedAd rewardedAd;
    private InterstitialAd interstitialAd;

    private boolean rewardedLoading;
    private boolean interstitialLoading;
    private boolean initialized;

    private AdsManager() { }

    public static synchronized AdsManager get(Context ctx) {
        if (instance == null) instance = new AdsManager();
        return instance;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        MobileAds.initialize(com.altomedia.herbalindo.HerbalindoApp.get(), status -> { });
    }

    /* ================= BANNER ================= */

    public void loadBanner(@Nullable AdView view) {
        if (view == null) return;
        try {
            // Ukuran dan unit iklan ditetapkan dari kode, tidak bergantung pada
            // atribut XML. AdView melempar IllegalStateException bila keduanya
            // belum lengkap saat loadAd dipanggil.
            view.setAdSize(AdSize.BANNER);
            view.setAdUnitId(Config.bannerUnit());
            view.loadAd(new AdRequest.Builder().build());
        } catch (RuntimeException e) {
            // Banner gagal tidak boleh menjatuhkan layar yang memuatnya.
            Log.w(TAG, "Banner gagal dimuat: " + e.getMessage());
        }
    }

    /* ================= REWARDED ================= */

    public void preloadRewarded() {
        if (rewardedAd != null || rewardedLoading) return;
        rewardedLoading = true;
        RewardedAd.load(
                com.altomedia.herbalindo.HerbalindoApp.get(),
                Config.rewardedUnit(),
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        rewardedLoading = false;
                    }

                    @Override public void onAdFailedToLoad(@NonNull LoadAdError err) {
                        rewardedAd = null;
                        rewardedLoading = false;
                        Log.w(TAG, "Rewarded gagal dimuat: " + err.getMessage());
                    }
                });
    }

    public boolean isRewardedReady() { return rewardedAd != null; }

    /**
     * Menampilkan rewarded. {@link RewardCallback#onRewarded()} hanya dipanggil
     * dari onUserEarnedReward, sehingga poin tidak pernah diberikan lebih awal.
     */
    public void showRewarded(Activity activity, RewardCallback cb) {
        if (rewardedAd == null) {
            preloadRewarded();
            cb.onFailed("Iklan belum siap. Coba lagi sebentar.");
            return;
        }
        final RewardedAd ad = rewardedAd;
        final boolean[] earned = {false};
        final boolean[] settled = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                rewardedAd = null;
                preloadRewarded();
                if (settled[0]) return;
                settled[0] = true;
                if (!earned[0]) {
                    cb.onFailed("Iklan ditutup sebelum selesai. Poin tidak diberikan.");
                }
            }

            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                rewardedAd = null;
                preloadRewarded();
                if (settled[0]) return;
                settled[0] = true;
                cb.onFailed("Iklan gagal ditampilkan: " + adError.getMessage());
            }
        });
        ad.show(activity, (RewardItem item) -> {
            earned[0] = true;
            cb.onRewarded();
        });
    }

    /* ================= INTERSTITIAL ================= */

    public void preloadInterstitial() {
        if (interstitialAd != null || interstitialLoading) return;
        interstitialLoading = true;
        InterstitialAd.load(
                com.altomedia.herbalindo.HerbalindoApp.get(),
                Config.interstitialUnit(),
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        interstitialLoading = false;
                    }

                    @Override public void onAdFailedToLoad(@NonNull LoadAdError err) {
                        interstitialAd = null;
                        interstitialLoading = false;
                        Log.w(TAG, "Interstitial gagal dimuat: " + err.getMessage());
                    }
                });
    }

    public boolean isInterstitialReady() { return interstitialAd != null; }

    /**
     * Menampilkan interstitial bila sudah siap. Bila belum siap, iklan dimuat
     * untuk kesempatan berikutnya dan {@code onClosed} tetap dijalankan sehingga
     * alur pengguna tidak pernah tertahan oleh iklan.
     */
    public void showInterstitial(Activity activity, @Nullable Runnable onClosed) {
        if (interstitialAd == null) {
            preloadInterstitial();
            if (onClosed != null) onClosed.run();
            return;
        }
        final InterstitialAd ad = interstitialAd;
        final boolean[] settled = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                interstitialAd = null;
                preloadInterstitial();
                if (settled[0]) return;
                settled[0] = true;
                if (onClosed != null) onClosed.run();
            }

            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                interstitialAd = null;
                preloadInterstitial();
                if (settled[0]) return;
                settled[0] = true;
                if (onClosed != null) onClosed.run();
            }
        });
        ad.show(activity);
    }

    public interface RewardCallback {
        void onRewarded();
        void onFailed(String reason);
    }
}