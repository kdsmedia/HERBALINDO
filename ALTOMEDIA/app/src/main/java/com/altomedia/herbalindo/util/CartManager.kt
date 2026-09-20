package com.altomedia.herbalindo.util

import android.content.Context
import com.altomedia.herbalindo.data.model.CartLine
import com.altomedia.herbalindo.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, in-memory cart persisted to SharedPreferences so it survives process death.
 * Cart contents are advisory only — createOrder re-reads prices and stock server-side.
 */
class CartManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("herbalindo_cart", Context.MODE_PRIVATE)

    private val _lines = MutableStateFlow(load())
    val linesFlow: Flow<List<CartLine>> = _lines.asStateFlow()

    val lines: List<CartLine> get() = _lines.value

    val subtotal: Long get() = _lines.value.sumOf { it.subtotal }
    val totalPoints: Long get() = _lines.value.sumOf { it.pointsEarned }
    val totalWeight: Long get() = _lines.value.sumOf { it.totalWeight }
    val itemCount: Long get() = _lines.value.sumOf { it.qty }

    @Synchronized
    fun add(product: Product, qty: Long = 1) {
        if (qty <= 0) return
        val existing = _lines.value.firstOrNull { it.productId == product.productId }
        val updated = if (existing != null) {
            val capped = (existing.qty + qty).coerceAtMost(product.stock.coerceAtLeast(1))
            _lines.value.map {
                if (it.productId == product.productId) it.copy(qty = capped) else it
            }
        } else {
            _lines.value + CartLine(
                productId = product.productId,
                name = product.name,
                imageUrl = product.imageUrl,
                price = product.effectivePrice,
                points = product.points,
                qty = qty.coerceAtMost(product.stock.coerceAtLeast(1)),
                weight = product.weight,
            )
        }
        commit(updated)
    }

    @Synchronized
    fun setQty(productId: String, qty: Long) {
        if (qty <= 0) {
            remove(productId)
            return
        }
        commit(_lines.value.map { if (it.productId == productId) it.copy(qty = qty) else it })
    }

    @Synchronized
    fun increment(productId: String) {
        val line = _lines.value.firstOrNull { it.productId == productId } ?: return
        setQty(productId, line.qty + 1)
    }

    @Synchronized
    fun decrement(productId: String) {
        val line = _lines.value.firstOrNull { it.productId == productId } ?: return
        setQty(productId, line.qty - 1)
    }

    @Synchronized
    fun remove(productId: String) {
        commit(_lines.value.filterNot { it.productId == productId })
    }

    /** Replaces a line's cached price/points after a remote catalogue change. */
    @Synchronized
    fun reprice(product: Product) {
        val line = _lines.value.firstOrNull { it.productId == product.productId } ?: return
        val qty = line.qty.coerceAtMost(product.stock.coerceAtLeast(1))
        if (qty <= 0) {
            remove(product.productId)
            return
        }
        commit(_lines.value.map {
            if (it.productId == product.productId) {
                it.copy(price = product.effectivePrice, points = product.points, qty = qty, name = product.name)
            } else it
        })
    }

    @Synchronized
    fun clear() = commit(emptyList())

    private fun commit(lines: List<CartLine>) {
        _lines.value = lines
        prefs.edit().putString(KEY, serialize(lines)).apply()
    }

    private fun serialize(lines: List<CartLine>): String {
        val arr = JSONArray()
        for (l in lines) {
            arr.put(JSONObject().apply {
                put("productId", l.productId)
                put("name", l.name)
                put("imageUrl", l.imageUrl)
                put("price", l.price)
                put("points", l.points)
                put("qty", l.qty)
                put("weight", l.weight)
            })
        }
        return arr.toString()
    }

    private fun load(): List<CartLine> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CartLine(
                    productId = o.optString("productId"),
                    name = o.optString("name"),
                    imageUrl = o.optString("imageUrl"),
                    price = o.optLong("price"),
                    points = o.optLong("points"),
                    qty = o.optLong("qty", 1).coerceAtLeast(1),
                    weight = o.optLong("weight"),
                )
            }.filter { it.productId.isNotEmpty() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    companion object {
        private const val KEY = "cart_lines"
    }
}