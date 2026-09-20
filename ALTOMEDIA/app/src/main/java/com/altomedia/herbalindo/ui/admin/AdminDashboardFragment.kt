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
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.databinding.FragmentAdminDashboardBinding
import com.altomedia.herbalindo.util.Fmt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Admin overview (BAB 10.1): members, catalogue, orders, withdrawals and revenue. */
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipe.setOnRefreshListener { binding.swipe.isRefreshing = false }
        binding.btnClaimAdmin.setOnClickListener { claimAdmin() }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                Repository.usersFlow(),
                Repository.productsFlow(),
                Repository.allOrdersFlow(),
                Repository.allWithdrawalsFlow(),
            ) { users, products, orders, withdrawals -> Stats(users, products, orders, withdrawals) }
                .collect { render(it) }
        }
    }

    private data class Stats(
        val users: List<com.altomedia.herbalindo.data.model.User>,
        val products: List<com.altomedia.herbalindo.data.model.Product>,
        val orders: List<com.altomedia.herbalindo.data.model.Order>,
        val withdrawals: List<com.altomedia.herbalindo.data.model.Withdrawal>,
    )

    private fun render(s: Stats) {
        _binding ?: return
        binding.swipe.isRefreshing = false

        binding.tvTotalMembers.text = s.users.size.toString()
        binding.tvActiveMembers.text = s.users.count { it.isActive }.toString()
        binding.tvTotalProducts.text = s.products.size.toString()
        binding.tvLowStock.text = s.products.count { it.stock <= it.minStock }.toString()

        val paidOrders = s.orders.filter { it.paymentStatus == "PAID" }
        binding.tvTotalOrders.text = s.orders.size.toString()
        binding.tvPendingOrders.text = s.orders.count {
            it.paymentStatus == "WAITING_CONFIRMATION" || it.orderStatus == "WAITING_PAYMENT"
        }.toString()
        binding.tvPendingWithdrawals.text = s.withdrawals.count { it.status == "PENDING" }.toString()
        binding.tvTotalRevenue.text = Fmt.rupiah(paidOrders.sumOf { it.total })
    }

    private fun claimAdmin() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.claimFirstAdmin()
            val message = when (result) {
                is OpResult.Success -> "Akun ini sekarang menjadi admin"
                is OpResult.Failure -> Api.message(result.error)
            }
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}