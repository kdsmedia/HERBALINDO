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
import com.altomedia.herbalindo.databinding.FragmentAdminProductEditBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Create or edit a catalogue product (BAB 11.1 / 11.2). Saving routes through the
 * saveProduct callable, which validates fields and writes an admin log entry.
 */
class AdminProductEditFragment : Fragment() {

    private var _binding: FragmentAdminProductEditBinding? = null
    private val binding get() = _binding!!
    private var productId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminProductEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        productId = arguments?.getString(ARG_PRODUCT_ID)?.takeIf { it.isNotBlank() }

        binding.tvHeading.text =
            if (productId == null) getString(R.string.tambah_produk) else getString(R.string.edit_produk)
        binding.btnSave.setOnClickListener { save() }

        val id = productId
        if (id != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                Repository.productFlow(id).collect { product ->
                    _binding ?: return@collect
                    product ?: return@collect
                    populate(product)
                }
            }
        }
    }

    /** Only fills empty fields so an in-flight edit is never overwritten by a snapshot. */
    private fun populate(p: com.altomedia.herbalindo.data.model.Product) {
        if (binding.etName.text.isNullOrEmpty()) binding.etName.setText(p.name)
        if (binding.etSku.text.isNullOrEmpty()) binding.etSku.setText(p.sku)
        if (binding.etPrice.text.isNullOrEmpty()) binding.etPrice.setText(p.price.toString())
        if (binding.etPromoPrice.text.isNullOrEmpty()) binding.etPromoPrice.setText(p.promoPrice.toString())
        binding.swPromoActive.isChecked = p.promoActive
        if (binding.etStock.text.isNullOrEmpty()) binding.etStock.setText(p.stock.toString())
        if (binding.etMinStock.text.isNullOrEmpty()) binding.etMinStock.setText(p.minStock.toString())
        if (binding.etPoints.text.isNullOrEmpty()) binding.etPoints.setText(p.points.toString())
        if (binding.etWeight.text.isNullOrEmpty()) binding.etWeight.setText(p.weight.toString())
        if (binding.etImage.text.isNullOrEmpty()) binding.etImage.setText(p.imageUrl)
        if (binding.etDescription.text.isNullOrEmpty()) binding.etDescription.setText(p.description)
        if (binding.etComposition.text.isNullOrEmpty()) binding.etComposition.setText(p.composition)
        if (binding.etUsage.text.isNullOrEmpty()) binding.etUsage.setText(p.usage)
        if (binding.etWarning.text.isNullOrEmpty()) binding.etWarning.setText(p.warning)
    }

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val price = binding.etPrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val promoPrice = binding.etPromoPrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val stock = binding.etStock.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val minStock = binding.etMinStock.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val points = binding.etPoints.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val weight = binding.etWeight.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val image = binding.etImage.text?.toString()?.trim().orEmpty()
        val promoActive = binding.swPromoActive.isChecked

        when {
            name.length < 3 -> { snack("Nama produk minimal 3 karakter"); return }
            price <= 0 -> { snack("Harga produk wajib diisi"); return }
            promoActive && promoPrice <= 0 -> { snack("Harga promo wajib diisi saat promo aktif"); return }
            promoActive && promoPrice >= price -> { snack("Harga promo harus lebih rendah dari harga normal"); return }
            stock < 0 -> { snack("Stok tidak boleh negatif"); return }
            image.isNotBlank() && !image.startsWith("http") -> {
                snack("URL gambar harus diawali http:// atau https://"); return
            }
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val fields = mutableMapOf<String, Any?>(
                "name" to name,
                "sku" to binding.etSku.text?.toString()?.trim().orEmpty(),
                "price" to price,
                "promoPrice" to promoPrice,
                "promoActive" to promoActive,
                "stock" to stock,
                "minStock" to minStock,
                "points" to points,
                "weight" to weight,
                "imageUrl" to image,
                "description" to binding.etDescription.text?.toString()?.trim().orEmpty(),
                "composition" to binding.etComposition.text?.toString()?.trim().orEmpty(),
                "usage" to binding.etUsage.text?.toString()?.trim().orEmpty(),
                "warning" to binding.etWarning.text?.toString()?.trim().orEmpty(),
                "status" to "ACTIVE",
            )
            productId?.let { fields["productId"] = it }

            val result = Api.saveProduct(fields)
            setBusy(false)
            when (result) {
                is OpResult.Success -> {
                    snack(if (productId == null) "Produk berhasil ditambahkan" else "Produk berhasil diperbarui")
                    parentFragmentManager.popBackStack()
                }
                is OpResult.Failure -> snack(Api.message(result.error))
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !busy
    }

    private fun snack(message: String) {
        _binding ?: return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"

        fun newInstance(productId: String?) = AdminProductEditFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRODUCT_ID, productId) }
        }
    }
}