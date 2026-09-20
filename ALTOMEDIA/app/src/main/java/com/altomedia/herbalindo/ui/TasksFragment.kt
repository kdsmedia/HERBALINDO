package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.altomedia.herbalindo.Config
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Api
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.AppSettings
import com.altomedia.herbalindo.data.model.DailyTask
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.data.model.ReferralStatus
import com.altomedia.herbalindo.data.model.User
import com.altomedia.herbalindo.databinding.FragmentTasksBinding
import com.altomedia.herbalindo.util.Fmt
import com.altomedia.herbalindo.util.SessionStore
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Daily tasks (BAB 7): check-in, 20 rewarded ads and referral progress.
 *
 * A reward is only granted after the AdMob SDK reports the user earned it, and the
 * points themselves are minted by the rewardAd Cloud Function, which enforces the
 * daily cap independently of this screen.
 */
class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionStore

    private var rewardedAd: RewardedAd? = null
    private var loadingAd = false
    private var currentUser: User? = null
    private var settings: AppSettings = AppSettings()
    private var watchedToday: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionStore(requireContext())

        binding.btnCheckin.setOnClickListener { doCheckIn() }
        binding.btnWatchAd.setOnClickListener { showRewardedAd() }
        binding.btnShareReferral.setOnClickListener { shareReferral() }
        binding.btnShop.setOnClickListener { (activity as? MainActivity)?.navigateTo(R.id.nav_produk) }

        MobileAds.initialize(requireContext()) { loadRewardedAd() }

        observe()
    }

    private fun observe() {
        val uid = session.uid
        if (uid.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                Repository.userFlow(uid),
                Repository.settingsFlow(),
                Repository.dailyTaskFlow(uid, Fmt.dayKey()),
                Repository.referralsAsInviterFlow(uid),
            ) { user, s, task, referrals -> Quad(user, s, task, referrals) }
                .collect { render(it) }
        }
    }

    private data class Quad(
        val user: User?,
        val settings: AppSettings,
        val task: DailyTask?,
        val referrals: List<com.altomedia.herbalindo.data.model.Referral>,
    )

    private fun render(q: Quad) {
        _binding ?: return
        val user = q.user ?: return
        currentUser = user
        settings = q.settings
        watchedToday = q.task?.adsWatched ?: 0L

        binding.tvTotalPoints.text = Fmt.points(user.points)

        // --- check-in
        val checkedIn = q.task?.checkedIn == true
        binding.tvCheckinSub.text = if (checkedIn) {
            getString(R.string.checkin_done)
        } else {
            "Dapatkan +${q.settings.checkinPoints} poin hari ini"
        }
        binding.btnCheckin.isEnabled = !checkedIn
        binding.btnCheckin.text =
            if (checkedIn) getString(R.string.selesai) else getString(R.string.check_in)

        // --- rewarded ads
        val maxAds = q.settings.maxAdsPerDay.coerceAtLeast(1)
        binding.progressAds.max = maxAds.toInt()
        binding.progressAds.progress = watchedToday.coerceIn(0, maxAds).toInt()
        binding.tvAdsSub.text =
            "$watchedToday/$maxAds iklan · +${q.settings.pointsPerAd} poin per iklan"

        val adsDone = watchedToday >= maxAds
        binding.btnWatchAd.isEnabled = !adsDone && !loadingAd
        binding.btnWatchAd.text =
            if (adsDone) getString(R.string.selesai) else getString(R.string.tonton_iklan)

        // --- referrals
        val verified = q.referrals.count { it.status == ReferralStatus.VERIFIED.name }
        val pending = q.referrals.count { it.status == ReferralStatus.PENDING.name }
        binding.tvReferralCount.text = "$verified Verified"
        binding.tvReferralHint.text = buildString {
            append("Referral ID: ${user.referralId.ifBlank { "-" }}\n")
            append("Bonus +${Fmt.points(q.settings.referralBonusPoints)} setelah member baru ")
            append("menyelesaikan pembelian pertama yang memenuhi syarat. ")
            append("Bonus hanya untuk 1 tingkat (pengundang langsung).")
            if (pending > 0) append("\n$pending referral menunggu pembelian pertama.")
        }

        // --- purchase task
        val earned = user.points
        binding.tvPurchaseSub.text =
            "Total poin terkumpul: ${Fmt.points(earned)}"
    }

    private fun doCheckIn() {
        binding.btnCheckin.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = Api.dailyCheckIn()) {
                is OpResult.Success -> snack(getString(R.string.checkin_done))
                is OpResult.Failure -> {
                    binding.btnCheckin.isEnabled = true
                    snack(Api.message(result.error))
                }
            }
        }
    }

    private fun loadRewardedAd() {
        if (loadingAd || rewardedAd != null) return
        loadingAd = true
        RewardedAd.load(
            requireContext(),
            Config.ADMOB_REWARDED_UNIT,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingAd = false
                    rewardedAd = ad
                    _binding?.btnWatchAd?.isEnabled = watchedToday < settings.maxAdsPerDay
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingAd = false
                    rewardedAd = null
                    // Retry once after a short pause; a persistent failure leaves the
                    // button enabled so the member can try again manually.
                    _binding?.root?.postDelayed({
                        if (isAdded && rewardedAd == null) loadRewardedAd()
                    }, Config.AD_RETRY_DELAY_MS)
                }
            }
        )
    }

    private fun showRewardedAd() {
        val ad = rewardedAd
        if (ad == null) {
            snack("Iklan belum siap, mohon tunggu sebentar")
            loadRewardedAd()
            return
        }

        binding.btnWatchAd.isEnabled = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd()
                refreshButtons()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewardedAd()
                refreshButtons()
            }
        }

        ad.show(requireActivity()) { rewardItem ->
            // The SDK confirmed a completed view; ask the backend to credit the points.
            creditAdReward(rewardItem.amount)
        }
    }

    private fun creditAdReward(sdkAmount: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = Api.rewardAd(Config.ADMOB_REWARDED_UNIT)) {
                is OpResult.Success -> {
                    val awarded = (result.data["awarded"] as? Number)?.toLong() ?: 0L
                    snack(getString(R.string.ads_reward_earned, awarded))
                }
                is OpResult.Failure -> snack(Api.message(result.error))
            }
            refreshButtons()
        }
    }

    private fun refreshButtons() {
        _binding ?: return
        val maxAds = settings.maxAdsPerDay.coerceAtLeast(1)
        val adsDone = watchedToday >= maxAds
        binding.btnWatchAd.isEnabled = !adsDone && !loadingAd && isAdded
        binding.btnWatchAd.text =
            if (adsDone) getString(R.string.selesai) else getString(R.string.tonton_iklan)
    }

    private fun shareReferral() {
        val id = currentUser?.referralId.orEmpty()
        if (id.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Ayo gabung di Herbalindo! Gunakan Referral ID saya: $id untuk mendapatkan produk herbal berkualitas."
            )
        }
        startActivity(Intent.createChooser(intent, getString(R.string.bagikan)))
    }

    private fun snack(message: String) {
        _binding ?: return
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}