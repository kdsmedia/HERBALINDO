package com.altomedia.herbalindo.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
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
import com.altomedia.herbalindo.data.model.Order
import com.altomedia.herbalindo.databinding.FragmentAdminOrdersBinding
import com.altomedia.herbalindo.databinding.ItemAdminOrderBinding
import com.altomedia.herbalindo.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Order fulfilment (BAB 12). Payment confirmation is what actually credits the buyer's
 * points and triggers a qualified referral bonus, so it is a deliberate, logged step.
 */
class AdminOrdersFragment : Fragment() {

    private var _binding: FragmentAdminOrdersBinding? = null
    private val binding get() = _binding!!
    private var allOrders: List<Order> = emptyList()
    private var filterIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OrderAdminAdapter(
            onConfirmPayment = { confirmPayment(it) },
            onNextStatus = { advanceStatus(it) },
            onShipping = { askTracking(it) },
            onRefund = { confirmRefund(it) },
        )
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter

        binding.filterTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                filterIndex = tab?.position ?: 0
                adapter.submit(visible())
                updateEmpty()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.allOrdersFlow().collect { list ->
                _binding ?: return@collect
                allOrders = list
                adapter.submit(visible())
                updateEmpty()
            }
        }
    }

    private fun visible(): List<Order> = when (filterIndex) {
        1 -> allOrders.filter { it.orderStatus == "PENDING" || it.orderStatus == "WAITING_PAYMENT" }
        2 -> allOrders.filter { it.paymentStatus == "WAITING_CONFIRMATION" }
        3 -> allOrders.filter { it.orderStatus == "PAID" || it.orderStatus == "PROCESSING" }
        4 -> allOrders.filter { it.orderStatus == "SHIPPED" }
        5 -> allOrders.filter { it.orderStatus == "COMPLETED" || it.orderStatus == "DELIVERED" }
        else -> allOrders
    }

    private fun updateEmpty() {
        _binding ?: return
        val empty = visible().isEmpty()
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvOrders.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun confirmPayment(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi pembayaran")
            .setMessage(
                "Pesanan ${order.orderNumber} sebesar ${Fmt.rupiah(order.total)} akan ditandai " +
                    "LUNAS. Poin pembelian dan bonus referral yang memenuhi syarat akan dicairkan."
            )
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton("Konfirmasi") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = Api.confirmPayment(order.orderId)
                    snack(statusResultMessage(result))
                }
            }
            .show()
    }

    private fun advanceStatus(order: Order) {
        val next = nextStatus(order.orderStatus) ?: return
        if (next == "SHIPPED") {
            askTracking(order)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.updateOrderStatus(order.orderId, next)
            snack(statusResultMessage(result))
        }
    }

    /** Forward-only status machine so an order cannot silently regress. */
    private fun nextStatus(current: String): String? = when (current) {
        "PENDING", "WAITING_PAYMENT" -> "PROCESSING"
        "PAID" -> "PROCESSING"
        "PROCESSING" -> "SHIPPED"
        "SHIPPED" -> "COMPLETED"
        else -> null
    }

    private fun askTracking(order: Order) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val courierInput = EditText(context).apply {
            hint = "Kurir (mis. JNE, J&T, SiCepat)"
            setText(order.shippingCourier)
        }
        val trackingInput = EditText(context).apply {
            hint = "Nomor resi"
            setText(order.trackingNumber)
        }
        container.addView(courierInput)
        container.addView(trackingInput)

        MaterialAlertDialogBuilder(context)
            .setTitle("Input resi pengiriman")
            .setView(container)
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.simpan) { _, _ ->
                val courier = courierInput.text?.toString()?.trim().orEmpty()
                val tracking = trackingInput.text?.toString()?.trim().orEmpty()
                when {
                    courier.isBlank() -> snack("Nama kurir wajib diisi")
                    tracking.length < 4 -> snack("Nomor resi tidak valid")
                    else -> viewLifecycleOwner.lifecycleScope.launch {
                        val result = Api.updateOrderStatus(
                            order.orderId, "SHIPPED", courier, tracking, null
                        )
                        snack(statusResultMessage(result))
                    }
                }
            }
            .show()
    }

    private fun confirmRefund(order: Order) {
        val context = requireContext()
        val reasonInput = EditText(context).apply { hint = "Alasan pembatalan / refund" }

        MaterialAlertDialogBuilder(context)
            .setTitle("Refund pesanan")
            .setMessage(
                "Stok akan dikembalikan dan poin yang sudah diberikan akan ditarik kembali " +
                    "sesuai kebijakan refund (BAB 12.4)."
            )
            .setView(reasonInput)
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton("Refund") { _, _ ->
                val reason = reasonInput.text?.toString()?.trim().orEmpty()
                if (reason.isBlank()) {
                    snack("Alasan refund wajib diisi")
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = Api.refundOrder(order.orderId, reason)
                        snack(statusResultMessage(result))
                    }
                }
            }
            .show()
    }

    private fun statusResultMessage(result: OpResult<Map<String, Any?>>): String = when (result) {
        is OpResult.Success -> "Perubahan berhasil disimpan"
        is OpResult.Failure -> Api.message(result.error)
    }

    private fun snack(message: String) {
        _binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvOrders.adapter = null
        _binding = null
    }

    private class OrderAdminAdapter(
        val onConfirmPayment: (Order) -> Unit,
        val onNextStatus: (Order) -> Unit,
        val onShipping: (Order) -> Unit,
        val onRefund: (Order) -> Unit,
    ) : ListAdapter<Order, OrderAdminAdapter.VH>(DIFF) {

        fun submit(items: List<Order>) = submitList(items)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemAdminOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemAdminOrderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(order: Order) {
                b.tvOrderNumber.text = order.orderNumber.ifBlank { order.orderId.take(10) }
                b.tvMeta.text = Fmt.dateTime(order.createdAt) + " · " + order.paymentMethod
                b.tvCustomer.text = "Pembeli: ${order.userId.take(10)}…"
                b.tvShipTo.text = buildString {
                    append(order.shippingAddress.recipientName)
                    if (order.shippingAddress.phone.isNotBlank()) append(" · ${order.shippingAddress.phone}")
                    append("\n")
                    append(order.shippingAddress.address)
                    if (order.shippingAddress.city.isNotBlank()) append(", ${order.shippingAddress.city}")
                }
                b.tvItems.text = order.items.joinToString(", ") { "${it.name} ×${it.qty}" }
                b.tvTotal.text = Fmt.rupiah(order.total)

                b.tvPaymentStatus.text = Fmt.statusLabel(order.paymentStatus)
                b.tvPaymentStatus.setTextColor(
                    b.root.context.getColor(
                        when (order.paymentStatus) {
                            "PAID" -> R.color.status_paid
                            "WAITING_CONFIRMATION" -> R.color.status_pending
                            "FAILED", "REFUNDED" -> R.color.status_cancelled
                            else -> R.color.text_secondary
                        }
                    )
                )
                b.tvOrderStatus.text = Fmt.statusLabel(order.orderStatus)

                val hasTracking = order.trackingNumber.isNotBlank()
                b.tvTracking.visibility = if (hasTracking) View.VISIBLE else View.GONE
                if (hasTracking) {
                    b.tvTracking.text = "${order.shippingCourier} · ${order.trackingNumber}"
                }

                val canConfirm = order.paymentStatus == "WAITING_CONFIRMATION" ||
                    order.paymentStatus == "UNPAID"
                b.btnConfirmPayment.isEnabled = canConfirm &&
                    order.orderStatus != "CANCELLED" && order.orderStatus != "REFUNDED"

                val next = when (order.orderStatus) {
                    "PENDING", "WAITING_PAYMENT", "PAID" -> "Proses Pesanan"
                    "PROCESSING" -> "Kirim Pesanan"
                    "SHIPPED" -> "Selesaikan"
                    else -> null
                }
                b.btnNextStatus.visibility = if (next == null) View.GONE else View.VISIBLE
                b.btnNextStatus.text = next ?: ""
                if (next != null) b.btnNextStatus.setOnClickListener { onNextStatus(order) }

                b.btnConfirmPayment.setOnClickListener { onConfirmPayment(order) }
                b.btnShipping.setOnClickListener { onShipping(order) }
                b.btnRefund.isEnabled =
                    order.orderStatus != "CANCELLED" && order.orderStatus != "REFUNDED"
                b.btnRefund.setOnClickListener { onRefund(order) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<Order>() {
                override fun areItemsTheSame(a: Order, b: Order) = a.orderId == b.orderId
                override fun areContentsTheSame(a: Order, b: Order) = a == b
            }
        }
    }
}