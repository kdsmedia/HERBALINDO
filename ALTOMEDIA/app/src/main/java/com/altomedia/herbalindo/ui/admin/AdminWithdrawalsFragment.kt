package com.altomedia.herbalindo.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Api
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.data.model.Withdrawal
import com.altomedia.herbalindo.databinding.FragmentAdminWithdrawalsBinding
import com.altomedia.herbalindo.databinding.ItemAdminWithdrawalBinding
import com.altomedia.herbalindo.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Withdrawal queue (BAB 9 / 10). Each transition requires a note so the member can see
 * why a request was rejected, and the member's points are only deducted by the backend.
 */
class AdminWithdrawalsFragment : Fragment() {

    private var _binding: FragmentAdminWithdrawalsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminWithdrawalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = WithdrawalAdminAdapter(
            onApprove = { process(it, "APPROVED", "Disetujui admin") },
            onReject = { askReject(it) },
            onPaid = { askPaid(it) },
        )
        binding.rvWithdrawals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWithdrawals.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.allWithdrawalsFlow().collect { list ->
                _binding ?: return@collect
                val sorted = list.sortedWith(
                    compareByDescending<Withdrawal> { it.status == "PENDING" }
                        .thenByDescending { it.createdAt }
                )
                adapter.submit(sorted)
                val empty = sorted.isEmpty()
                binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvWithdrawals.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun askReject(withdrawal: Withdrawal) {
        val input = EditText(requireContext()).apply { hint = "Alasan penolakan" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tolak penarikan ${Fmt.rupiah(withdrawal.amount)}")
            .setMessage("Poin akan dikembalikan ke saldo member.")
            .setView(input)
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.reject) { _, _ ->
                val note = input.text?.toString()?.trim().orEmpty()
                if (note.isBlank()) {
                    snack("Alasan penolakan wajib diisi")
                } else {
                    process(withdrawal, "REJECTED", note)
                }
            }
            .show()
    }

    private fun askPaid(withdrawal: Withdrawal) {
        val input = EditText(requireContext()).apply {
            hint = "Catatan transfer (opsional)"
            setText("Transfer berhasil")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tandai sudah ditransfer")
            .setMessage(
                "Pastikan ${Fmt.rupiah(withdrawal.amount)} sudah dikirim ke " +
                    "${withdrawal.method} ${Fmt.maskAccount(withdrawal.destination)} " +
                    "atas nama ${withdrawal.holderName}."
            )
            .setView(input)
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                process(withdrawal, "PAID", input.text?.toString() ?: "")
            }
            .show()
    }

    private fun process(withdrawal: Withdrawal, status: String, note: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.processWithdrawal(withdrawal.withdrawalId, status, note)
            val message = when (result) {
                is OpResult.Success -> "Penarikan ditandai ${Fmt.statusLabel(status)}"
                is OpResult.Failure -> Api.message(result.error)
            }
            snack(message)
        }
    }

    private fun snack(message: String) {
        _binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvWithdrawals.adapter = null
        _binding = null
    }

    private class WithdrawalAdminAdapter(
        val onApprove: (Withdrawal) -> Unit,
        val onReject: (Withdrawal) -> Unit,
        val onPaid: (Withdrawal) -> Unit,
    ) : ListAdapter<Withdrawal, WithdrawalAdminAdapter.VH>(DIFF) {

        fun submit(items: List<Withdrawal>) = submitList(items)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemAdminWithdrawalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemAdminWithdrawalBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(w: Withdrawal) {
                b.tvAmount.text = Fmt.rupiah(w.amount)
                b.tvStatus.text = Fmt.statusLabel(w.status)
                b.tvStatus.setTextColor(
                    b.root.context.getColor(
                        when (w.status) {
                            "PAID" -> R.color.status_paid
                            "APPROVED", "REVIEW" -> R.color.status_pending
                            "REJECTED" -> R.color.status_cancelled
                            else -> R.color.text_secondary
                        }
                    )
                )
                b.tvMember.text = "Member: ${w.userId.take(10)}…"
                b.tvDestination.text = "${w.method} · ${Fmt.maskAccount(w.destination)} · ${w.holderName}"
                b.tvMeta.text = buildString {
                    append("Diajukan ${Fmt.dateTime(w.createdAt)}")
                    if (w.processedAt != null) append("\nDiproses ${Fmt.dateTime(w.processedAt)}")
                    if (w.note.isNotBlank()) append("\nCatatan: ${w.note}")
                }

                val pending = w.status == "PENDING" || w.status == "REVIEW"
                val approved = w.status == "APPROVED"
                b.btnApprove.visibility = if (pending) View.VISIBLE else View.GONE
                b.btnReject.visibility = if (pending || approved) View.VISIBLE else View.GONE
                b.btnPaid.visibility = if (approved) View.VISIBLE else View.GONE

                b.btnApprove.setOnClickListener { onApprove(w) }
                b.btnReject.setOnClickListener { onReject(w) }
                b.btnPaid.setOnClickListener { onPaid(w) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<Withdrawal>() {
                override fun areItemsTheSame(a: Withdrawal, b: Withdrawal) =
                    a.withdrawalId == b.withdrawalId

                override fun areContentsTheSame(a: Withdrawal, b: Withdrawal) = a == b
            }
        }
    }
}