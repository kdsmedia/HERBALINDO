package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.CartLine
import com.altomedia.herbalindo.databinding.ActivityCartBinding
import com.altomedia.herbalindo.databinding.ItemCartBinding
import com.altomedia.herbalindo.ui.widgets.loadProductImage
import com.altomedia.herbalindo.util.CartManager
import com.altomedia.herbalindo.util.Fmt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Cart with quantity controls, subtotal, shipping and total (BAB 5.2 / 14.5). */
class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var cart: CartManager
    private var shippingCost: Long = 15_000L
    private var freeShippingMin: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cart = CartManager(this)

        binding.toolbar.tvTitle.text = getString(R.string.keranjang)
        binding.toolbar.btnBack.setOnClickListener { finish() }

        val adapter = CartAdapter(
            onPlus = { cart.increment(it) },
            onMinus = { cart.decrement(it) },
            onRemove = { cart.remove(it) },
        )
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = adapter

        binding.btnCheckout.setOnClickListener {
            if (cart.lines.isEmpty()) return@setOnClickListener
            startActivity(Intent(this, CheckoutActivity::class.java))
        }

        lifecycleScope.launch {
            Repository.settingsFlow().collectLatest { settings ->
                shippingCost = settings.shippingCostFlat
                freeShippingMin = settings.freeShippingMin
                renderTotals(cart.lines)
            }
        }

        lifecycleScope.launch {
            cart.linesFlow.collectLatest { lines ->
                adapter.submitList(lines)
                renderTotals(lines)
                binding.tvEmpty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
                binding.rvCart.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
                binding.summaryCard.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun renderTotals(lines: List<CartLine>) {
        val subtotal = lines.sumOf { it.subtotal }
        val shipping = if (freeShippingMin > 0 && subtotal >= freeShippingMin) 0L else shippingCost
        binding.tvSubtotal.text = Fmt.rupiah(subtotal)
        binding.tvShipping.text =
            if (shipping == 0L) "GRATIS" else Fmt.rupiah(shipping)
        binding.tvTotal.text = Fmt.rupiah(subtotal + shipping)
    }

    private class CartAdapter(
        val onPlus: (String) -> Unit,
        val onMinus: (String) -> Unit,
        val onRemove: (String) -> Unit,
    ) : ListAdapter<CartLine, CartAdapter.VH>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(line: CartLine) {
                b.tvName.text = line.name
                b.tvPrice.text = Fmt.rupiah(line.price)
                b.tvQty.text = line.qty.toString()
                b.tvSubtotal.text = Fmt.rupiah(line.subtotal)
                b.ivImage.loadProductImage(line.imageUrl)
                b.btnPlus.setOnClickListener { onPlus(line.productId) }
                b.btnMinus.setOnClickListener { onMinus(line.productId) }
                b.btnRemove.setOnClickListener { onRemove(line.productId) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<CartLine>() {
                override fun areItemsTheSame(a: CartLine, b: CartLine) = a.productId == b.productId
                override fun areContentsTheSame(a: CartLine, b: CartLine) = a == b
            }
        }
    }
}