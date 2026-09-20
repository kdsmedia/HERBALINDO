package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.AppSettings
import com.altomedia.herbalindo.data.model.Product
import com.altomedia.herbalindo.data.model.ReferralStatus
import com.altomedia.herbalindo.data.model.User
import com.altomedia.herbalindo.databinding.FragmentHomeBinding
import com.altomedia.herbalindo.ui.widgets.ProductAdapter
import com.altomedia.herbalindo.ui.widgets.showSnack
import com.altomedia.herbalindo.util.Fmt
import com.altomedia.herbalindo.util.SessionStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Member dashboard (BAB 14.3): balance, points, referral, daily progress, featured items. */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionStore
    private val productAdapter = ProductAdapter(onClick = { openProduct(it) })
    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionStore(requireContext())

        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = productAdapter
        binding.rvProducts.isNestedScrollingEnabled = false

        binding.swipe.setOnRefreshListener { refresh() }

        binding.btnCopyReferral.setOnClickListener { copyReferral() }
        binding.btnShareReferral.setOnClickListener { shareReferral() }
        binding.tvSeeAll.setOnClickListener { (activity as? MainActivity)?.navigateTo(R.id.nav_produk) }

        observeData()
    }

    private fun observeData() {
        val uid = session.uid
        if (uid.isEmpty()) return
        val today = Fmt.dayKey()

        viewLifecycleOwner.lifecycleScope.launch {
            // Combine the live documents so the dashboard stays consistent without polling.
            combine(
                Repository.userFlow(uid),
                Repository.settingsFlow(),
                Repository.dailyTaskFlow(uid, today),
                Repository.referralsAsInviterFlow(uid),
                Repository.activeProductsFlow(),
            ) { user, settings, task, referrals, products ->
                Dashboard(user, settings, task, referrals, products)
            }.collect { render(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.withdrawalsFlow(uid).collect { list -> renderWithdrawal(list.firstOrNull()) }
        }
    }

    private data class Dashboard(
        val user: User?,
        val settings: AppSettings,
        val task: com.altomedia.herbalindo.data.model.DailyTask?,
        val referrals: List<com.altomedia.herbalindo.data.model.Referral>,
        val products: List<Product>,
    )

    private fun render(d: Dashboard) {
        _binding ?: return
        binding.swipe.isRefreshing = false

        val user = d.user
        currentUser = user
        if (user == null) return

        binding.tvGreeting.text = "Halo, ${user.name.ifBlank { "Member" }}"
        binding.tvBalance.text = Fmt.rupiah(user.points / d.settings.pointsPerRupiah * 1_000)
        binding.tvPoints.text = Fmt.points(user.points)
        binding.tvReferralId.text = "Referral: ${user.referralId.ifBlank { "------" }}"

        val checkedIn = d.task?.checkedIn == true
        binding.tvCheckin.text = "📅 ${getString(R.string.check_in)} · " +
            if (checkedIn) getString(R.string.selesai) else getString(R.string.belum_selesai)

        val watched = d.task?.adsWatched ?: 0L
        val maxAds = d.settings.maxAdsPerDay.coerceAtLeast(1)
        binding.progressAds.max = maxAds.toInt()
        binding.progressAds.progress = watched.coerceIn(0, maxAds).toInt()
        binding.tvAds.text = "📺 ${getString(R.string.rewarded_ads)}  $watched/$maxAds"

        val verified = d.referrals.count { it.status == ReferralStatus.VERIFIED.name }
        binding.tvReferralTask.text = "👥 ${getString(R.string.referral_task)}  $verified Verified"

        val featured = d.products.take(6)
        productAdapter.submit(featured)
        binding.tvEmpty.visibility = if (featured.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderWithdrawal(w: com.altomedia.herbalindo.data.model.Withdrawal?) {
        _binding ?: return
        if (w == null) {
            binding.cardWithdrawal.visibility = View.GONE
            return
        }
        binding.cardWithdrawal.visibility = View.VISIBLE
        binding.tvWithdrawalStatus.text =
            "Withdrawal ${Fmt.rupiah(w.amount)} · ${Fmt.statusLabel(w.status)}"
    }

    private fun refresh() {
        // Firestore listeners already stream updates; the gesture refreshes settings and
        // the product catalogue, which are the fields most likely to change remotely.
        viewLifecycleOwner.lifecycleScope.launch {
            Repository.getSettings()
            binding.swipe.isRefreshing = false
        }
    }

    private fun copyReferral() {
        val id = currentUser?.referralId.orEmpty()
        if (id.isBlank()) return
        val clipboard = requireContext()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Referral ID", id))
        binding.root.showSnack(getString(R.string.referral_copied))
    }

    private fun shareReferral() {
        val id = currentUser?.referralId.orEmpty()
        if (id.isBlank()) return
        val text = "Ayo gabung di Herbalindo! Gunakan Referral ID saya: $id"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.bagikan)))
    }

    private fun openProduct(product: Product) {
        startActivity(
            Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.productId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvProducts.adapter = null
        _binding = null
    }
}