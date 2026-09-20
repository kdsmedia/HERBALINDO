package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.CartLine
import com.altomedia.herbalindo.databinding.ActivityCheckoutBinding
import com.altomedia.herbalindo.databinding.ItemCheckoutLineBinding
import com.altomedia.herbalindo.util.CartManager
import com.altomedia.herbalindo.util.Fmt
import com.altomedia.herbalindo.util.SessionStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Collects the shipping address and creates the order server-side (BAB 5.3 / 6).
 * Prices, stock and earned points are all resolved by createOrder, never by this screen.
 */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var cart: CartManager
    private lateinit var session: SessionStore

    private var shippingCost: Long = 15_000L
    private var freeShippingMin: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cart = CartManager(this)
        session = SessionStore(this)

        binding.toolbar.tvTitle.text = getString(R.string.checkout)
        binding.toolbar.btnBack.setOnClickListener { finish() }

        if (cart.lines.isEmpty()) {
            finish()
            return
        }

        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = LineAdapter(cart.lines)

        prefill()
        binding.btnCreate.setOnClickListener { submit() }

        lifecycleScope.launch {
            Repository.settingsFlow().collectLatest { settings ->
                shippingCost = settings.shippingCostFlat
                freeShippingMin = settings.freeShippingMin
                renderTotals()
            }
        }
    }

    private fun prefill() {
        // The member's own name and phone are the common case for a first order.
        viewLifecycleOwnerScope {
            val user = Repository.userCache(session.uid)
            binding.etName.setText(user?.name.orEmpty())
            binding.etPhone.setText(user?.phone.orEmpty())
        }
    }

    private fun viewLifecycleOwnerScope(block: () -> Unit) = block()

    private fun renderTotals() {
        val subtotal = cart.subtotal
        val shipping = if (freeShippingMin > 0 && subtotal >= freeShippingMin) 0L else shippingCost
        binding.tvSubtotal.text = Fmt.rupiah(subtotal)
        binding.tvShipping.text = if (shipping == 0L) "GRATIS" else Fmt.rupiah(shipping)
        binding.tvTotal.text = Fmt.rupiah(subtotal + shipping)
    }

    private fun submit() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        val address = binding.etAddress.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        val postal = binding.etPostal.text?.toString()?.trim().orEmpty()
        val note = binding.etNote.text?.toString()?.trim().orEmpty()

        when {
            name.length < 2 -> { snack("Nama penerima wajib diisi"); return }
            phone.length < 8 -> { snack("Nomor HP penerima tidak valid"); return }
            address.length < 10 -> { snack("Alamat terlalu singkat, mohon lengkapi"); return }
            city.isEmpty() -> { snack("Kota / Kabupaten wajib diisi"); return }
        }

        setBusy(true)
        lifecycleScope.launch {
            val result = Repository.createOrder(
                items = cart.lines.map { mapOf("productId" to it.productId, "qty" to it.qty) },
                recipientName = name,
                phone = phone,
                address = address,
                city = city,
                postalCode = postal,
                note = note,
            )
            setBusy(false)

            when (result) {
                is com.altomedia.herbalindo.data.model.OpResult.Success -> {
                    cart.clear()
                    startActivity(
                        Intent(this@CheckoutActivity, PaymentActivity::class.java)
                            .putExtra(PaymentActivity.EXTRA_ORDER_ID, result.data.orderId)
                    )
                    finish()
                }
                is com.altomedia.herbalindo.data.model.OpResult.Failure -> {
                    snack(Repository.friendlyMessage(result.error))
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnCreate.isEnabled = !busy
    }

    private fun snack(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    private class LineAdapter(private val lines: List<CartLine>) :
        RecyclerView.Adapter<LineAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemCheckoutLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = lines.size

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(lines[position])

        class VH(private val b: ItemCheckoutLineBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(line: CartLine) {
                b.tvName.text = line.name
                b.tvQty.text = "×${line.qty}"
                b.tvSubtotal.text = Fmt.rupiah(line.subtotal)
            }
        }
    }
}