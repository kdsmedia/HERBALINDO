package com.altomedia.herbalindo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.altomedia.herbalindo.BuildConfig
import com.altomedia.herbalindo.Config
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.AppSettings
import com.altomedia.herbalindo.data.model.Order
import com.altomedia.herbalindo.data.model.User
import com.altomedia.herbalindo.databinding.FragmentProfileBinding
import com.altomedia.herbalindo.ui.widgets.OrderAdapter
import com.altomedia.herbalindo.util.Fmt
import com.altomedia.herbalindo.util.SessionStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Profile, order history, legal links and sign-out (BAB 14.6). */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionStore
    private val orderAdapter = OrderAdapter { openOrder(it) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionStore(requireContext())

        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = orderAdapter

        binding.tvVersion.text = "${getString(R.string.versi)} ${BuildConfig.VERSION_NAME}"

        binding.menuReferral.setOnClickListener { (activity as? MainActivity)?.navigateTo(R.id.nav_tugas) }
        binding.menuPrivacy.setOnClickListener { openUrl(Config.PRIVACY_URL) }
        binding.menuTerms.setOnClickListener { openUrl(Config.TERMS_URL) }
        binding.menuAbout.setOnClickListener { showAbout() }
        binding.menuContact.setOnClickListener { contactSupport() }
        binding.btnLogout.setOnClickListener { confirmLogout() }

        observe()
    }

    private fun observe() {
        val uid = session.uid
        if (uid.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                Repository.userFlow(uid),
                Repository.settingsFlow(),
            ) { u, s -> u to s }.collect { (u, s) -> render(u, s) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.ordersFlow(uid).collect { orders ->
                orderAdapter.submit(orders)
                binding.tvNoOrders.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                binding.tvOrders.text = orders.size.toString()
            }
        }
    }

    private fun render(user: User?, settings: AppSettings) {
        _binding ?: return
        val u = user ?: return

        binding.tvName.text = u.name.ifBlank { "Member" }
        binding.tvContact.text = buildString {
            if (u.email.isNotBlank()) append(u.email)
            if (u.phone.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(u.phone)
            }
        }
        binding.tvReferral.text = "Referral ID: ${u.referralId.ifBlank { "-" }}"

        val ratio = settings.pointsPerRupiah / 1_000L
        binding.tvPoints.text = Fmt.thousands(u.points)
        binding.tvBalance.text = Fmt.rupiah(if (ratio > 0) u.points / ratio else 0L)
    }

    private fun openOrder(order: Order) {
        startActivity(
            Intent(requireContext(), PaymentActivity::class.java)
                .putExtra(PaymentActivity.EXTRA_ORDER_ID, order.orderId)
        )
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "Tidak dapat membuka tautan", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.tentang_aplikasi))
            .setMessage(
                "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}\n\n" +
                    "${getString(R.string.app_tagline)}\n\n" +
                    "Dikembangkan oleh ${Config.DEVELOPER}.\n\n" +
                    "Aplikasi ini menyediakan produk herbal, sistem member dengan poin, " +
                    "tugas harian, dan program referral satu tingkat.\n\n" +
                    "Informasi produk bukan pengganti nasihat tenaga kesehatan."
            )
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Config.SUPPORT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, "Bantuan ${getString(R.string.app_name)}")
        }
        runCatching { startActivity(intent) }.onFailure {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, Config.SUPPORT_EMAIL, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage("Anda yakin ingin keluar dari akun ini?")
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.logout) { _, _ ->
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                session.clear()
                startActivity(
                    Intent(requireContext(), SplashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvOrders.adapter = null
        _binding = null
    }
}