package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.Product
import com.altomedia.herbalindo.databinding.ActivityProductDetailBinding
import com.altomedia.herbalindo.ui.widgets.loadProductImage
import com.altomedia.herbalindo.util.CartManager
import com.altomedia.herbalindo.util.Fmt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Product detail with quantity picker and cart / buy-now actions (BAB 5.1). */
class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var cart: CartManager
    private var product: Product? = null
    private var qty: Long = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cart = CartManager(this)

        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
        if (productId.isEmpty()) {
            finish()
            return
        }

        binding.toolbar.btnBack.setOnClickListener { finish() }
        binding.btnMinus.setOnClickListener { changeQty(-1) }
        binding.btnPlus.setOnClickListener { changeQty(1) }
        binding.btnAddCart.setOnClickListener { addToCart() }
        binding.btnBuyNow.setOnClickListener { buyNow() }
        binding.swipe.setOnRefreshListener { binding.swipe.isRefreshing = false }

        lifecycleScope.launch {
            Repository.productFlow(productId).collectLatest { p ->
                binding.swipe.isRefreshing = false
                product = p
                if (p == null) {
                    binding.tvName.text = getString(R.string.gagal_memuat)
                    binding.btnAddCart.isEnabled = false
                    binding.btnBuyNow.isEnabled = false
                    return@collectLatest
                }
                render(p)
            }
        }
    }

    private fun render(p: Product) {
        binding.toolbar.tvTitle.text = p.name
        binding.tvName.text = p.name
        binding.tvSku.text = "SKU: ${p.sku.ifBlank { "-" }}"
        binding.tvPrice.text = Fmt.rupiah(p.effectivePrice)

        val discounted = p.effectivePrice < p.price
        binding.tvStrikePrice.visibility = if (discounted) View.VISIBLE else View.GONE
        if (discounted) {
            binding.tvStrikePrice.text = Fmt.rupiah(p.price)
            binding.tvStrikePrice.paintFlags =
                binding.tvStrikePrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        }

        binding.tvPoints.text = "+" + Fmt.points(p.points)
        binding.tvStock.text = "${getString(R.string.stok)}: ${p.stock}"
        binding.tvDescription.text = p.description.ifBlank { "-" }
        binding.tvComposition.text = p.composition.ifBlank { "-" }
        binding.tvUsage.text = p.usage.ifBlank { "-" }
        binding.tvWarning.text = p.warning.ifBlank {
            "Ikuti aturan pakai pada label. Hentikan pemakaian apabila terjadi reaksi tidak diinginkan dan konsultasikan dengan tenaga kesehatan."
        }
        binding.ivImage.loadProductImage(p.imageUrl)

        qty = qty.coerceIn(1, p.stock.coerceAtLeast(1))
        renderQty()

        val available = p.inStock && p.isActive
        binding.btnAddCart.isEnabled = available
        binding.btnBuyNow.isEnabled = available
        binding.btnBuyNow.text = if (available) getString(R.string.beli_sekarang)
        else getString(R.string.stok_habis)
    }

    private fun changeQty(delta: Long) {
        val stock = product?.stock ?: 1
        qty = (qty + delta).coerceIn(1, stock.coerceAtLeast(1))
        renderQty()
    }

    private fun renderQty() {
        binding.tvQty.text = qty.toString()
    }

    private fun addToCart() {
        val p = product ?: return
        cart.add(p, qty)
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, "${p.name} ×$qty ditambahkan", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .show()
    }

    private fun buyNow() {
        val p = product ?: return
        cart.clear()
        cart.add(p, qty)
        startActivity(Intent(this, CheckoutActivity::class.java))
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "product_id"
    }
}