package com.altomedia.herbalindo.ui.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.model.Product
import com.altomedia.herbalindo.databinding.ItemProductGridBinding
import com.altomedia.herbalindo.util.Fmt

/** Catalogue card: photo, name, effective price, points and a buy action (BAB 14.4). */
class ProductAdapter(
    private val onClick: (Product) -> Unit,
    private val onBuy: (Product) -> Unit = onClick,
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    fun submit(items: List<Product>) = submitList(items)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemProductGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.tvName.text = product.name
            binding.tvPrice.text = Fmt.rupiah(product.effectivePrice)

            val discounted = product.effectivePrice < product.price
            binding.tvStrikePrice.visibility = if (discounted) View.VISIBLE else View.GONE
            if (discounted) {
                binding.tvStrikePrice.text = Fmt.rupiah(product.price)
                binding.tvStrikePrice.paintFlags =
                    binding.tvStrikePrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvStrikePrice.paintFlags =
                    binding.tvStrikePrice.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            binding.tvPoints.text = "+" + Fmt.points(product.points)
            binding.ivImage.loadProductImage(product.imageUrl)

            val available = product.inStock && product.isActive
            binding.btnBuy.isEnabled = available
            binding.btnBuy.text = if (available) binding.root.context.getString(R.string.beli)
            else binding.root.context.getString(R.string.stok_habis)

            binding.root.setOnClickListener { onClick(product) }
            binding.btnBuy.setOnClickListener { if (available) onBuy(product) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(a: Product, b: Product) = a.productId == b.productId
            override fun areContentsTheSame(a: Product, b: Product) = a == b
        }
    }
}