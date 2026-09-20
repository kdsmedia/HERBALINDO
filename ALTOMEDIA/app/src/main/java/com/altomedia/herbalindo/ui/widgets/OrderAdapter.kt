package com.altomedia.herbalindo.ui.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.model.Order
import com.altomedia.herbalindo.databinding.ItemOrderBinding
import com.altomedia.herbalindo.util.Fmt

/** Order history rows with status, item summary, total and tracking number. */
class OrderAdapter(
    private val onClick: (Order) -> Unit,
) : ListAdapter<Order, OrderAdapter.VH>(DIFF) {

    fun submit(items: List<Order>) = submitList(items)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(order: Order) {
            b.tvOrderNumber.text = order.orderNumber.ifBlank { order.orderId.take(10) }
            b.tvDate.text = Fmt.dateTime(order.createdAt)
            b.tvStatus.setStatusChip(order.orderStatus)
            b.tvItems.text = order.items.joinToString(", ") { "${it.name} ×${it.qty}" }
            b.tvTotal.text = Fmt.rupiah(order.total)

            val showTracking = order.trackingNumber.isNotBlank()
            b.tvTracking.visibility = if (showTracking) View.VISIBLE else View.GONE
            if (showTracking) {
                b.tvTracking.text = "${order.shippingCourier.ifBlank { "Kurir" }} · ${order.trackingNumber}"
            }

            b.root.setOnClickListener { onClick(order) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(a: Order, b: Order) = a.orderId == b.orderId
            override fun areContentsTheSame(a: Order, b: Order) = a == b
        }
    }
}