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
import com.altomedia.herbalindo.data.model.Product
import com.altomedia.herbalindo.databinding.FragmentAdminProductsBinding
import com.altomedia.herbalindo.databinding.ItemAdminProductBinding
import com.altomedia.herbalindo.ui.widgets.loadProductImage
import com.altomedia.herbalindo.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Product catalogue management (BAB 11): edit, stock movements and soft delete. */
class AdminProductsFragment : Fragment() {

    private var _binding: FragmentAdminProductsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ProductAdminAdapter(
            onEdit = { (activity as? AdminActivity)?.openProductEditor(it.productId) },
            onStock = { askStockAdjustment(it) },
            onToggle = { confirmToggle(it) },
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter

        binding.btnNewProduct.setOnClickListener {
            (activity as? AdminActivity)?.openProductEditor(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.productsFlow().collect { list ->
                _binding ?: return@collect
                val sorted = list.sortedBy { it.name.lowercase() }
                adapter.submit(sorted)
                binding.tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                binding.rvProducts.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    /** Stock corrections always carry a reason and are logged as a stock movement. */
    private fun askStockAdjustment(product: Product) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val deltaInput = EditText(context).apply {
            hint = "+10 untuk menambah, −5 untuk mengurangi"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val reasonInput = EditText(context).apply {
            hint = "Alasan (mis. stok opname, barang rusak)"
        }
        container.addView(deltaInput)
        container.addView(reasonInput)

        MaterialAlertDialogBuilder(context)
            .setTitle("${getString(R.string.kelola_stok)} · ${product.name}")
            .setMessage("Stok saat ini: ${product.stock}")
            .setView(container)
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.simpan) { _, _ ->
                val delta = deltaInput.text?.toString()?.trim()?.toLongOrNull()
                val reason = reasonInput.text?.toString()?.trim().orEmpty()
                when {
                    delta == null || delta == 0L -> snack("Jumlah stok wajib diisi dan tidak boleh nol")
                    reason.isBlank() -> snack("Alasan wajib diisi")
                    (product.stock + delta) < 0 -> snack("Stok tidak boleh negatif")
                    else -> applyStock(product.productId, delta, reason)
                }
            }
            .show()
    }

    private fun applyStock(productId: String, delta: Long, reason: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = Api.adjustStock(productId, delta, reason)
            val message = when (result) {
                is OpResult.Success -> "Stok diperbarui"
                is OpResult.Failure -> Api.message(result.error)
            }
            snack(message)
        }
    }

    private fun confirmToggle(product: Product) {
        val deactivating = product.isActive
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${if (deactivating) "Nonaktifkan" else "Aktifkan"} ${product.name}")
            .setMessage(
                if (deactivating) {
                    "Produk tidak akan muncul di katalog. Riwayat pesanan tetap tersimpan."
                } else {
                    "Produk akan kembali tampil di katalog."
                }
            )
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = Api.setProductStatus(
                        product.productId,
                        if (deactivating) "INACTIVE" else "ACTIVE",
                    )
                    val message = when (result) {
                        is OpResult.Success -> "Status produk diperbarui"
                        is OpResult.Failure -> Api.message(result.error)
                    }
                    snack(message)
                }
            }
            .show()
    }

    private fun snack(message: String) {
        _binding ?: return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvProducts.adapter = null
        _binding = null
    }

    private class ProductAdminAdapter(
        val onEdit: (Product) -> Unit,
        val onStock: (Product) -> Unit,
        val onToggle: (Product) -> Unit,
    ) : ListAdapter<Product, ProductAdminAdapter.VH>(DIFF) {

        fun submit(items: List<Product>) = submitList(items)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemAdminProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemAdminProductBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(product: Product) {
                b.tvName.text = product.name
                b.tvPrice.text = if (product.effectivePrice < product.price) {
                    "${Fmt.rupiah(product.effectivePrice)} (promo dari ${Fmt.rupiah(product.price)})"
                } else {
                    Fmt.rupiah(product.price)
                }
                b.tvStock.text = "Stok: ${product.stock} · ${product.status}"
                b.tvStock.setTextColor(
                    b.root.context.getColor(
                        when {
                            product.stock <= 0 -> R.color.danger
                            product.stock <= product.minStock -> R.color.warning
                            else -> R.color.text_secondary
                        }
                    )
                )
                b.ivImage.loadProductImage(product.imageUrl)

                b.btnEdit.setOnClickListener { onEdit(product) }
                b.btnStock.setOnClickListener { onStock(product) }
                b.btnToggle.text = if (product.isActive) "Nonaktifkan" else "Aktifkan"
                b.btnToggle.setOnClickListener { onToggle(product) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<Product>() {
                override fun areItemsTheSame(a: Product, b: Product) = a.productId == b.productId
                override fun areContentsTheSame(a: Product, b: Product) = a == b
            }
        }
    }
}