package com.altomedia.herbalindo.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Api
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.AppSettings
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.databinding.FragmentAdminSettingsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Business-rule configuration (BAB 10.4 / 10.5). The values here drive the limits the
 * Cloud Functions enforce, so a change takes effect for every client immediately.
 */
class AdminSettingsFragment : Fragment() {

    private var _binding: FragmentAdminSettingsBinding? = null
    private val binding get() = _binding!!
    private var loaded = false
    private var current: AppSettings = AppSettings()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSave.setOnClickListener { save() }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.settingsFlow().collect { settings ->
                _binding ?: return@collect
                current = settings
                if (!loaded) {
                    populate(settings)
                    loaded = true
                }
            }
        }
    }

    private fun populate(s: AppSettings) {
        binding.etCheckinPoints.setText(s.checkinPoints.toString())
        binding.etPointsPerAd.setText(s.pointsPerAd.toString())
        binding.etMaxAds.setText(s.maxAdsPerDay.toString())
        binding.etReferralBonus.setText(s.referralBonusPoints.toString())
        binding.etReferralMinOrder.setText(s.referralMinOrderTotal.toString())
        binding.swReferralPurchase.isChecked = s.referralRequiresPurchase
        binding.etPointsPerRupiah.setText(s.pointsPerRupiah.toString())
        binding.etMinWithdrawal.setText(s.minWithdrawalRupiah.toString())
        binding.etMaxWithdrawals.setText(s.maxWithdrawalsPerDay.toString())
        binding.etShipping.setText(s.shippingCostFlat.toString())
        binding.etFreeShipping.setText(s.freeShippingMin.toString())
        binding.etSupportEmail.setText(s.supportEmail)
        binding.etAdminWhatsapp.setText(s.adminWhatsapp)
        binding.swMaintenance.isChecked = s.maintenanceMode
    }

    private fun save() {
        val checkin = binding.etCheckinPoints.text?.toString()?.toLongOrNull() ?: -1L
        val perAd = binding.etPointsPerAd.text?.toString()?.toLongOrNull() ?: -1L
        val maxAds = binding.etMaxAds.text?.toString()?.toLongOrNull() ?: -1L
        val referralBonus = binding.etReferralBonus.text?.toString()?.toLongOrNull() ?: -1L
        val referralMin = binding.etReferralMinOrder.text?.toString()?.toLongOrNull() ?: -1L
        val pointsPerRupiah = binding.etPointsPerRupiah.text?.toString()?.toLongOrNull() ?: -1L
        val minWithdrawal = binding.etMinWithdrawal.text?.toString()?.toLongOrNull() ?: -1L
        val maxWithdrawals = binding.etMaxWithdrawals.text?.toString()?.toLongOrNull() ?: -1L
        val shipping = binding.etShipping.text?.toString()?.toLongOrNull() ?: -1L
        val freeShipping = binding.etFreeShipping.text?.toString()?.toLongOrNull() ?: -1L
        val supportEmail = binding.etSupportEmail.text?.toString()?.trim().orEmpty()

        when {
            checkin < 0 -> { snack("Poin check-in tidak valid"); return }
            perAd <= 0 -> { snack("Poin per iklan harus lebih dari 0"); return }
            maxAds !in 1..100 -> { snack("Maksimum iklan harian harus antara 1 dan 100"); return }
            referralBonus < 0 -> { snack("Bonus referral tidak valid"); return }
            referralMin < 0 -> { snack("Minimum order bonus referral tidak valid"); return }
            pointsPerRupiah <= 0 -> { snack("Konversi poin tidak valid"); return }
            minWithdrawal < 1000 -> { snack("Minimum withdrawal minimal Rp1.000"); return }
            maxWithdrawals !in 1..10 -> { snack("Maksimum withdrawal harian harus antara 1 dan 10"); return }
            shipping < 0 -> { snack("Ongkos kirim tidak valid"); return }
            freeShipping < 0 -> { snack("Gratis ongkir tidak valid"); return }
            !supportEmail.contains("@") -> { snack("Email dukungan tidak valid"); return }
        }
        if (maxAds < AD_REQUIREMENT) {
            snack(
                "Maksimum iklan harian ($maxAds) lebih kecil dari syarat withdrawal " +
                    "(${AD_REQUIREMENT} iklan). Member tidak akan pernah bisa menarik saldo."
            )
            return
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.saveSettings(
                mapOf(
                    "checkinPoints" to checkin,
                    "pointsPerAd" to perAd,
                    "maxAdsPerDay" to maxAds,
                    "referralBonusPoints" to referralBonus,
                    "referralMinOrderTotal" to referralMin,
                    "referralRequiresPurchase" to binding.swReferralPurchase.isChecked,
                    "pointsPerRupiah" to pointsPerRupiah,
                    "minWithdrawalRupiah" to minWithdrawal,
                    "maxWithdrawalsPerDay" to maxWithdrawals,
                    "shippingCostFlat" to shipping,
                    "freeShippingMin" to freeShipping,
                    "supportEmail" to supportEmail,
                    "adminWhatsapp" to binding.etAdminWhatsapp.text?.toString()?.trim().orEmpty(),
                    "maintenanceMode" to binding.swMaintenance.isChecked,
                )
            )
            setBusy(false)
            when (result) {
                is OpResult.Success -> snack("Pengaturan berhasil disimpan")
                is OpResult.Failure -> snack(Api.message(result.error))
            }
            @Suppress("UNUSED_EXPRESSION") current
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !busy
    }

    private fun snack(message: String) {
        _binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val AD_REQUIREMENT = 20L
    }
}