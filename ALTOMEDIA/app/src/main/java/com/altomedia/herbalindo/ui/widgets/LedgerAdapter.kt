package com.altomedia.herbalindo.ui.widgets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.model.PointsLedger
import com.altomedia.herbalindo.databinding.ItemLedgerBinding
import com.altomedia.herbalindo.util.Fmt

/** Points history rows, showing direction, reason and running balance (BAB 8.4). */
class LedgerAdapter : ListAdapter<PointsLedger, LedgerAdapter.VH>(DIFF) {

    fun submit(items: List<PointsLedger>) = submitList(items)

    

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemLedgerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val b: ItemLedgerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(entry: PointsLedger) {
            val positive = entry.delta >= 0
            b.tvTitle.text = entry.reason.ifBlank { "Penyesuaian" }
            b.tvSubtitle.text = "${Fmt.dateTime(entry.createdAt)} · Saldo ${Fmt.thousands(entry.balanceAfter)}"
            b.tvAmount.text = (if (positive) "+" else "") + Fmt.thousands(entry.delta)
            b.tvAmount.setTextColor(
                b.root.context.getColor(if (positive) R.color.success else R.color.danger)
            )
            b.ivIcon.setImageResource(
                if (positive) R.drawable.ic_add else R.drawable.ic_wallet
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PointsLedger>() {
            override fun areItemsTheSame(a: PointsLedger, b: PointsLedger) = a.entryId == b.entryId
            override fun areContentsTheSame(a: PointsLedger, b: PointsLedger) = a == b
        }
    }
}