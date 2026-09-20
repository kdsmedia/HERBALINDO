package com.altomedia.herbalindo.ui.admin

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
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.databinding.FragmentAdminMemberDetailBinding
import com.altomedia.herbalindo.ui.widgets.LedgerAdapter
import com.altomedia.herbalindo.util.Fmt
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Member detail with a manual points adjustment (BAB 10.3). Both the amount and a
 * reason are mandatory, and the backend records the change in the audit log.
 */
class AdminMemberDetailFragment : Fragment() {

    private var _binding: FragmentAdminMemberDetailBinding? = null
    private val binding get() = _binding!!
    private val ledgerAdapter = LedgerAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminMemberDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = arguments?.getString(ARG_UID).orEmpty()
        if (uid.isEmpty()) {
            parentFragmentManager.popBackStack()
            return
        }

        binding.rvLedger.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLedger.adapter = ledgerAdapter

        binding.btnAdjust.setOnClickListener { adjust(uid) }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.userFlow(uid).collect { user ->
                _binding ?: return@collect
                if (user == null) return@collect
                binding.tvName.text = user.name.ifBlank { "(tanpa nama)" }
                binding.tvContact.text =
                    listOf(user.email, user.phone).filter { it.isNotBlank() }.joinToString(" · ")
                binding.tvReferral.text = "Referral ID: ${user.referralId.ifBlank { "-" }}"
                binding.tvPoints.text = Fmt.points(user.points)
                binding.tvBalance.text = "Saldo: " +
                    Fmt.rupiah(user.points / com.altomedia.herbalindo.data.model.User.POINTS_PER_RUPIAH * 1_000)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.ledgerFlow(uid).collect { ledgerAdapter.submit(it) }
        }
    }

    private fun adjust(uid: String) {
        val delta = binding.etDelta.text?.toString()?.trim()?.toLongOrNull()
        val reason = binding.etReason.text?.toString()?.trim().orEmpty()

        when {
            delta == null || delta == 0L -> { snack("Jumlah penyesuaian wajib diisi dan tidak boleh nol"); return }
            reason.isBlank() -> { snack("Alasan wajib diisi"); return }
        }

        binding.btnAdjust.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.adjustUserPoints(uid, delta!!, reason)
            binding.btnAdjust.isEnabled = true
            when (result) {
                is OpResult.Success -> {
                    binding.etDelta.setText("")
                    binding.etReason.setText("")
                    snack("Poin berhasil disesuaikan")
                }
                is OpResult.Failure -> snack(Api.message(result.error))
            }
        }
    }

    private fun snack(message: String) {
        _binding ?: return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvLedger.adapter = null
        _binding = null
    }

    companion object {
        private const val ARG_UID = "uid"

        fun newInstance(uid: String) = AdminMemberDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_UID, uid) }
        }
    }
}