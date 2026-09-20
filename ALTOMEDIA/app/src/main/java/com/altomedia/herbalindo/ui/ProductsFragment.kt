package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.CartLine
import com.altomedia.herbalindo.data.model.Product
import com.altomedia.herbalindo.databinding.FragmentProductsBinding
import com.altomedia.herbalindo.ui.widgets.ProductAdapter
import com.altomedia.herbalindo.util.CartManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Catalogue with search, cart shortcut and quick add (BAB 5.1). */
class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!
    private var allProducts: List<Product> = emptyList()
    private val adapter = ProductAdapter(
        onClick = { openDetail(it) },
        onBuy = { addToCart(it) },
    )
    private lateinit var cart: CartManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cart = CartManager(requireContext())

        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter

        binding.swipe.setOnRefreshListener { binding.swipe.isRefreshing = false }
        binding.cartButton.setOnClickListener {
            startActivity(Intent(requireContext(), CartActivity::class.java))
        }
        binding.etSearch.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.activeProductsFlow().collectLatest { list ->
                allProducts = list.sortedBy { it.name.lowercase() }
                applyFilter(binding.etSearch.text?.toString().orEmpty())
                binding.swipe.isRefreshing = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            cart.linesFlow.collect { lines -> renderBadge(lines) }
        }
    }

    private fun applyFilter(query: String) {
        _binding ?: return
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allProducts else allProducts.filter {
            it.name.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
        }
        adapter.submit(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvProducts.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderBadge(lines: List<CartLine>) {
        _binding ?: return
        val count = lines.sumOf { it.qty }
        if (count <= 0) {
            binding.tvCartBadge.visibility = View.GONE
        } else {
            binding.tvCartBadge.visibility = View.VISIBLE
            binding.tvCartBadge.text = if (count > 99) "99+" else count.toString()
        }
    }

    private fun addToCart(product: Product) {
        cart.add(product, 1)
        binding.root.showSnackSafe("${product.name} ditambahkan ke keranjang")
    }

    private fun openDetail(product: Product) {
        startActivity(
            Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.productId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvProducts.adapter = null
        _binding = null
    }
}

/** Keeps the snackbar helper import local to this file. */
private fun View.showSnackSafe(message: String) {
    com.google.android.material.snackbar.Snackbar.make(this, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
}