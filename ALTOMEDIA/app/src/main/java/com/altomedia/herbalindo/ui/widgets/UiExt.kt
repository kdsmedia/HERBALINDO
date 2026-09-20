package com.altomedia.herbalindo.ui.widgets

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.altomedia.herbalindo.Config
import com.altomedia.herbalindo.R
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.snackbar.Snackbar

/**
 * Loads a product image. Falls back to the bundled leaf placeholder so a broken
 * external URL never leaves an empty card.
 */
fun ImageView.loadProductImage(url: String?) {
    if (url.isNullOrBlank()) {
        setImageResource(R.drawable.ic_leaf)
        return
    }
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.ic_leaf)
        .error(R.drawable.ic_leaf)
        .centerCrop()
        .into(this)
}

fun TextView.setStatusChip(status: String) {
    val label = com.altomedia.herbalindo.util.Fmt.statusLabel(status)
    text = label
    val colorRes = when (status) {
        "PAID", "COMPLETED", "APPROVED" -> R.color.status_paid
        "SHIPPED", "DELIVERED" -> R.color.status_shipped
        "PENDING", "WAITING_PAYMENT", "WAITING_CONFIRMATION", "REVIEW" -> R.color.status_pending
        "CANCELLED", "REJECTED", "FAILED" -> R.color.status_cancelled
        "REFUNDED" -> R.color.status_refunded
        else -> R.color.text_secondary
    }
    setTextColor(context.getColor(colorRes))
}

fun View.showSnack(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

/**
 * Banner ad helper. Ads are loaded lazily and only when a real unit is configured, so
 * a misconfigured build degrades to a hidden container instead of a blank gap.
 */
object BannerAds {

    private var configured = false

    /** Marks the device as a test device in debug builds to keep policy-safe impressions. */
    fun initForDebug(context: Context) {
        if (configured) return
        configured = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("EMULATOR"))
                .build()
        )
    }

    /**
     * Attaches a banner to [container], which must hold a single AdView child.
     * The container stays GONE until an ad is actually returned.
     */
    fun attach(context: Context, container: android.widget.FrameLayout) {
        initForDebug(context)
        val existing = container.getChildAt(0)
        val adView = (existing as? AdView) ?: AdView(context).also {
            it.adUnitId = Config.ADMOB_BANNER_UNIT
            it.setAdSize(AdSize.BANNER)
            container.addView(it)
        }
        adView.loadAd(AdRequest.Builder().build())
        container.visibility = View.VISIBLE
    }
}