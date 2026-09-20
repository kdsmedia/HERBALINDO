package com.altomedia.herbalindo.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
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
import com.altomedia.herbalindo.data.model.User
import com.altomedia.herbalindo.databinding.FragmentAdminMembersBinding
import com.altomedia.herbalindo.databinding.ItemAdminMemberBinding
import com.altomedia.herbalindo.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Member management (BAB 10.2): search, inspect, activate or deactivate accounts. */
class AdminMembersFragment : Fragment() {

    private var _binding: FragmentAdminMembersBinding? = null
    private val binding get() = _binding!!
    private var allUsers: List<User> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = MemberAdapter(
            onDetail = { (activity as? AdminActivity)?.openMemberDetail(it.uid) },
            onToggle = { confirmToggle(it) },
        )
        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.adapter = adapter

        binding.etSearch.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.usersFlow().collect { list ->
                allUsers = list
                applyFilter(binding.etSearch.text?.toString().orEmpty())
                _binding?.let { adapter.submit(filtered(binding.etSearch.text?.toString().orEmpty())) }
            }
        }
    }

    private fun filtered(query: String): List<User> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return allUsers
        return allUsers.filter {
            it.name.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.phone.contains(q) ||
                it.referralId.contains(q)
        }
    }

    private fun applyFilter(query: String) {
        _binding ?: return
        val result = filtered(query)
        binding.tvEmpty.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMembers.visibility = if (result.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmToggle(user: User) {
        val deactivating = user.status == "ACTIVE"
        val actionLabel = if (deactivating) "Nonaktifkan" else "Aktifkan"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$actionLabel ${user.name}")
            .setMessage(
                if (deactivating) {
                    "Akun tidak akan bisa login, withdraw, atau membuat pesanan baru."
                } else {
                    "Akun akan kembali dapat menggunakan seluruh fitur member."
                }
            )
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(actionLabel) { _, _ ->
                val next = if (deactivating) "INACTIVE" else "ACTIVE"
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = Api.setUserStatus(user.uid, next)
                    val message = when (result) {
                        is OpResult.Success -> "Status ${user.name} diubah menjadi $next"
                        is OpResult.Failure -> Api.message(result.error)
                    }
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMembers.adapter = null
        _binding = null
    }

    private class MemberAdapter(
        val onDetail: (User) -> Unit,
        val onToggle: (User) -> Unit,
    ) : ListAdapter<User, MemberAdapter.VH>(DIFF) {

        fun submit(items: List<User>) = submitList(items)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemAdminMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemAdminMemberBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(user: User) {
                b.tvName.text = user.name.ifBlank { "(tanpa nama)" }
                b.tvContact.text = listOf(user.email, user.phone).filter { it.isNotBlank() }.joinToString(" · ")
                b.tvReferral.text = "Referral: ${user.referralId.ifBlank { "-" }}"
                b.tvPoints.text = Fmt.thousands(user.points) + " poin"
                b.tvStatus.text = Fmt.statusLabel(user.status)
                b.tvStatus.setTextColor(
                    b.root.context.getColor(
                        if (user.isActive) R.color.status_paid else R.color.status_cancelled
                    )
                )

                val admin = user.isAdmin
                b.btnDetail.isEnabled = !admin
                b.btnToggleStatus.isEnabled = !admin
                b.btnToggleStatus.text =
                    if (user.isActive) "Nonaktifkan" else "Aktifkan"

                b.btnDetail.setOnClickListener { onDetail(user) }
                b.btnToggleStatus.setOnClickListener { onToggle(user) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<User>() {
                override fun areItemsTheSame(a: User, b: User) = a.uid == b.uid
                override fun areContentsTheSame(a: User, b: User) = a == b
            }
        }
    }
}