package com.altomedia.herbalindo.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Api
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.data.model.Order
import com.altomedia.herbalindo.data.model.OrderItem
import com.altomedia.herbalindo.data.model.PaymentStatus
import com.altomedia.herbalindo.databinding.ActivityPaymentBinding
import com.altomedia.herbalindo.databinding.ItemCheckoutLineBinding
import com.altomedia.herbalindo.util.Fmt
import com.altomedia.herbalindo.util.QrisEncoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * QRIS payment screen (BAB 6): renders a dynamic payload carrying the exact order
 * total, holds the QR valid for 15 minutes, and lets the member confirm payment.
 * The order is only marked PAID after an admin verifies settlement, so the client
 * never asserts that money arrived.
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private var order: Order? = null
    private var timer: CountDownTimer? = null
    private var qrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        if (orderId.isEmpty()) {
            finish()
            return
        }

        binding.toolbar.tvTitle.text = getString(R.string.pembayaran_qris)
        binding.toolbar.btnBack.setOnClickListener { finish() }
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.btnPaid.setOnClickListener { markPaid(orderId) }
        binding.btnSaveQr.setOnClickListener { saveQr() }

        lifecycleScope.launch {
            Repository.orderFlow(orderId).collect { o ->
                order = o
                if (o != null) render(o)
            }
        }
    }

    private fun render(o: Order) {
        binding.tvAmount.text = Fmt.rupiah(o.total)
        binding.tvOrderNumber.text = o.orderNumber
        binding.tvStatus.text = Fmt.statusLabel(o.paymentStatus)
        binding.tvStatus.setStatusColorFor(o.paymentStatus)

        binding.tvSubtotal.text = Fmt.rupiah(o.subtotal)
        binding.tvShipping.text = if (o.shippingCost == 0L) "GRATIS" else Fmt.rupiah(o.shippingCost)
        binding.tvTotal.text = Fmt.rupiah(o.total)
        binding.tvPoints.text = "+" + Fmt.points(o.pointsEarned)

        binding.rvItems.adapter = ItemAdapter(o.items)

        // The QR only makes sense while the order is still awaiting payment.
        val payable = o.paymentStatus == PaymentStatus.UNPAID.name ||
            o.paymentStatus == PaymentStatus.WAITING_CONFIRMATION.name

        binding.btnPaid.isEnabled = o.paymentStatus == PaymentStatus.UNPAID.name
        binding.btnPaid.text = when (o.paymentStatus) {
            PaymentStatus.WAITING_CONFIRMATION.name -> getString(R.string.menunggu_konfirmasi_admin)
            PaymentStatus.PAID.name -> getString(R.string.pembayaran_diterima)
            else -> getString(R.string.sudah_bayar)
        }

        if (qrBitmap == null && payable) {
            buildQr(o.total)
        }
        if (!payable) {
            // Payment settled: stop the countdown so the UI does not imply an expiry.
            timer?.cancel()
            binding.tvCountdown.text = Fmt.statusLabel(o.paymentStatus)
        } else if (timer == null) {
            startCountdown()
        }
    }

    private fun buildQr(amount: Long) {
        val payload = QrisEncoder.build(amount)
        if (payload == null) {
            binding.tvCountdown.text = "Nominal tidak dapat dikodekan ke QRIS."
            binding.btnPaid.isEnabled = false
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { encodeQr(payload) }
            qrBitmap = bitmap
            binding.ivQr.setImageBitmap(bitmap)
        }
    }

    private fun encodeQr(payload: String): Bitmap? = try {
        val size = 800
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (t: Throwable) {
        null
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(EXPIRY_MS, 1_000L) {
            override fun onTick(remaining: Long) {
                binding.tvCountdown.text =
                    getString(R.string.selesaikan_dalam) + " " + Fmt.minutesSeconds(remaining / 1000)
            }

            override fun onFinish() {
                binding.tvCountdown.text = getString(R.string.waktu_habis)
                binding.btnPaid.isEnabled = false
            }
        }.start()
    }

    private fun markPaid(orderId: String) {
        setBusy(true)
        lifecycleScope.launch {
            val result = Api.submitPaymentProof(orderId)
            setBusy(false)
            val message = when (result) {
                is OpResult.Success -> "Pembayaran akan diverifikasi admin"
                is OpResult.Failure -> Api.message(result.error)
            }
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnPaid.isEnabled = !busy
    }

    /**
     * Saves the QR to the shared Pictures collection. On API 28 and below this needs
     * WRITE_EXTERNAL_STORAGE, which the manifest declares with a maxSdkVersion cap.
     */
    private fun saveQr() {
        val bitmap = qrBitmap
        if (bitmap == null) {
            toast("QR belum siap")
            return
        }

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { writeQrToGallery(bitmap) }
            toast(if (saved) getString(R.string.qr_disimpan) else "Gagal menyimpan QR")
        }
    }

    private fun writeQrToGallery(bitmap: Bitmap): Boolean = try {
        val name = "QRIS_${order?.orderNumber?.ifBlank { "HERBALINDO" } ?: "HERBALINDO"}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Herbalindo")
            }
        }
        val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            false
        } else {
            val stream: OutputStream? = contentResolver.openOutputStream(uri)
            stream?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            stream != null
        }
    } catch (t: Throwable) {
        false
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun android.widget.TextView.setStatusColorFor(status: String) {
        val colorRes = when (status) {
            PaymentStatus.PAID.name -> R.color.status_paid
            PaymentStatus.WAITING_CONFIRMATION.name -> R.color.status_pending
            PaymentStatus.FAILED.name -> R.color.status_cancelled
            else -> R.color.status_pending
        }
        setTextColor(context.getColor(colorRes))
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    @Suppress("unused")
    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private class ItemAdapter(private val items: List<OrderItem>) :
        RecyclerView.Adapter<ItemAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemCheckoutLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        class VH(private val b: ItemCheckoutLineBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: OrderItem) {
                b.tvName.text = item.name
                b.tvQty.text = "×${item.qty}"
                b.tvSubtotal.text = Fmt.rupiah(item.subtotal)
            }
        }
    }

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        private const val EXPIRY_MS = 15 * 60 * 1000L
    }
}