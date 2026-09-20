package com.altomedia.herbalindo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Api
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.AppSettings
import com.altomedia.herbalindo.data.model.DailyTask
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.data.model.PointsLedger
import com.altomedia.herbalindo.data.model.User
import com.altomedia.herbalindo.data.model.Withdrawal
import com.altomedia.herbalindo.databinding.FragmentBalanceBinding
import com.altomedia.herbalindo.ui.widgets.LedgerAdapter
import com.altomedia.herbalindo.util.Fmt
import com.altomedia.herbalindo.util.SessionStore
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Balance and withdrawal (BAB 8 / 9). The eligibility checklist mirrors the server
 * rules exactly; requestWithdrawal re-checks all of them, so the UI is a convenience
 * rather than the enforcement point.
 */
class BalanceFragment : Fragment() {

    private var _binding: FragmentBalanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionStore

    private var user: User? = null
    private var settings: AppSettings = AppSettings()
    private var adsToday: Long = 0L
    private var withdrawalToday = false

    private val ledgerAdapter = LedgerAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionStore(requireContext())

        binding.rvLedger.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLedger.adapter = ledgerAdapter

        val withdrawalsAdapter = WithdrawalAdapter()
        binding.rvWithdrawals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWithdrawals.adapter = withdrawalsAdapter

        setupMethods()
        binding.swipe.setOnRefreshListener { binding.swipe.isRefreshing = false }
        binding.btnRequest.setOnClickListener { requestWithdrawal() }

        observe(withdrawalsAdapter)
    }

    private fun setupMethods() {
        val methods = listOf("BCA", "BRI", "BNI", "Mandiri", "DANA", "OVO", "GoPay", "ShopeePay")
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            methods,
        )
        binding.acMethod.setAdapter(adapter)
        binding.acMethod.setText(methods.first(), false)
    }

    private fun observe(withdrawalsAdapter: WithdrawalAdapter) {
        val uid = session.uid
        if (uid.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                Repository.userFlow(uid),
                Repository.settingsFlow(),
                Repository.dailyTaskFlow(uid, Fmt.dayKey()),
            ) { u, s, t -> Triple(u, s, t) }.collect { (u, s, t) ->
                user = u
                settings = s
                adsToday = t?.adsWatched ?: 0L
                renderEligibility(t)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.withdrawalsFlow(uid).collect { list ->
                withdrawalsAdapter.submit(list)
                binding.tvNoWithdrawals.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                withdrawalToday = list.any { it.dayKey == Fmt.dayKey() }
                renderEligibility(null)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.ledgerFlow(uid).collect { entries ->
                ledgerAdapter.submit(entries)
                binding.tvNoLedger.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun renderEligibility(task: DailyTask?) {
        _binding ?: return
        val u = user ?: return

        val ratio = settings.pointsPerRupiah / 1_000L
        val balanceRupiah = if (ratio > 0) u.points / ratio else 0L
        binding.tvBalance.text = Fmt.rupiah(balanceRupiah)
        binding.tvPoints.text = Fmt.points(u.points)

        val minRupiah = settings.minWithdrawalRupiah
        val requiredAdCount = AD_REQUIREMENT

        val balanceOk = balanceRupiah >= minRupiah
        val adsOk = adsToday >= requiredAdCount
        val accountOk = u.isActive && u.status == "ACTIVE"
        val dailyOk = !withdrawalToday

        binding.tvReqBalance.text = check(balanceOk) +
            "Saldo minimal ${Fmt.rupiah(minRupiah)} (saldo Anda ${Fmt.rupiah(balanceRupiah)})"
        binding.tvReqAds.text = check(adsOk) +
            "Selesaikan $requiredAdCount rewarded ads hari ini ($adsToday/$requiredAdCount)"
        binding.tvReqAccount.text = check(accountOk) + "Status akun aktif"
        binding.tvReqDaily.text = check(dailyOk) + "Maksimal 1 withdrawal per hari"

        val eligible = balanceOk && adsOk && accountOk && dailyOk
        binding.btnRequest.isEnabled = eligible
        binding.btnRequest.text =
            if (eligible) getString(R.string.ajukan_withdrawal) else "Belum memenuhi syarat"

        binding.tvQuickAmounts.text =
            "Maksimal penarikan hari ini: ${Fmt.rupiah(balanceRupiah)}"
    }

    private fun check(ok: Boolean) = if (ok) "✅ " else "⬜ "

    private fun requestWithdrawal() {
        val u = user ?: return
        val amount = binding.etAmount.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val method = binding.acMethod.text?.toString().orEmpty()
        val holder = binding.etHolder.text?.toString()?.trim().orEmpty()
        val destination = binding.etDestination.text?.toString()?.trim().orEmpty()

        when {
            amount <= 0 -> { snack("Jumlah penarikan belum diisi"); return }
            amount < settings.minWithdrawalRupiah -> {
                snack("Minimum penarikan ${Fmt.rupiah(settings.minWithdrawalRupiah)}")
                return
            }
            method.isBlank() -> { snack("Pilih metode penarikan"); return }
            holder.length < 3 -> { snack("Nama pemilik rekening wajib diisi"); return }
            destination.length < 6 -> { snack("Nomor rekening / e-wallet tidak valid"); return }
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.requestWithdrawal(amount, method, destination, holder)
            setBusy(false)
            when (result) {
                is OpResult.Success -> {
                    binding.etAmount.setText("")
                    binding.etDestination.setText("")
                    snack(getString(R.string.withdrawal_diajukan))
                }
                is OpResult.Failure -> snack(Api.message(result.error))
            }
            // Recompute against the freshly streamed user document.
            renderEligibility(null)
            @Suppress("UNUSED_EXPRESSION") u.uid
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnRequest.isEnabled = !busy
    }

    private fun snack(message: String) {
        _binding ?: return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** History list for past withdrawal requests. */
    private class WithdrawalAdapter :
        androidx.recyclerview.widget.ListAdapter<Withdrawal, WithdrawalAdapter.VH>(
            object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Withdrawal>() {
                override fun areItemsTheSame(a: Withdrawal, b: Withdrawal) =
                    a.withdrawalId == b.withdrawalId

                override fun areContentsTheSame(a: Withdrawal, b: Withdrawal) = a == b
            }
        ) {

        fun submit(items: List<Withdrawal>) = submitList(items)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            com.altomedia.herbalindo.databinding.ItemLedgerBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class VH(private val b: com.altomedia.herbalindo.databinding.ItemLedgerBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
            fun bind(w: Withdrawal) {
                b.tvTitle.text = "${w.method} · ${Fmt.maskAccount(w.destination)}"
                b.tvSubtitle.text = "${Fmt.dateTime(w.createdAt)} · ${Fmt.statusLabel(w.status)}"
                b.tvAmount.text = Fmt.rupiah(w.amount)
                b.tvAmount.setTextColor(
                    b.root.context.getColor(
                        when (w.status) {
                            "PAID" -> R.color.status_paid
                            "REJECTED" -> R.color.status_cancelled
                            else -> R.color.status_pending
                        }
                    )
                )
                b.ivIcon.setImageResource(R.drawable.ic_wallet)
            }
        }
    }

    companion object {
        /** BAB 9.2: 20 rewarded ads on the same day are required before withdrawing. */
        const val AD_REQUIREMENT = 20L
    }
}