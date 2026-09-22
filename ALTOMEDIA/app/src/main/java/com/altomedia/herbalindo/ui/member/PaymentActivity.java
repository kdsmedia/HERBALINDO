package com.altomedia.herbalindo.ui.member;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.QrisGenerator;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.BaseActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.Map;

/** Layar pembayaran QRIS: membuat QR nyata dari payload dan mengecek status order. */
public class PaymentActivity extends BaseActivity {

    private Models.Order order;
    private String payload;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_payment);
        applyInsets();
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbar_title)).setText("Pembayaran");

        order = repo.order(getIntent().getStringExtra("orderId"));
        if (order == null) { Ui.error(this, "Pesanan tidak ditemukan"); finish(); return; }

        ((TextView) findViewById(R.id.toolbar_sub)).setText(order.orderNumber);
        findViewById(R.id.toolbar_action).setVisibility(android.view.View.GONE);
        ((TextView) findViewById(R.id.pay_amount)).setText(Util.rupiah(order.total));

        // QRIS dinamis: nominal dan nomor pesanan disisipkan pada payload.
        payload = QrisGenerator.buildForOrder(Config.QRIS_BASE, order.total, order.orderNumber);
        if (!QrisGenerator.validate(payload)) {
            Ui.error(this, "Payload QRIS gagal divalidasi");
            finish();
            return;
        }
        ((ImageView) findViewById(R.id.pay_qr)).setImageBitmap(renderQr(payload, 640));
        ((TextView) findViewById(R.id.pay_merchant)).setText(
                QrisGenerator.merchantNameOf(payload) + " · " + QrisGenerator.amountOf(payload));
        ((TextView) findViewById(R.id.pay_payload)).setText(
                payload.length() > 90 ? payload.substring(0, 90) + "…" : payload);
        renderStatus();

        findViewById(R.id.pay_copy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("QRIS", payload));
                Ui.ok(this, "Payload QRIS disalin");
            }
        });
        findViewById(R.id.pay_share).setOnClickListener(v -> shareQr());
        findViewById(R.id.pay_check).setOnClickListener(v -> {
            order = repo.order(order.orderId);
            renderStatus();
            if ("PAID".equals(order.paymentStatus)) {
                Ui.info(this, "Pembayaran terverifikasi",
                        "Pesanan " + order.orderNumber + " sudah diverifikasi admin.\nPoin belanja telah ditambahkan.");
            } else if ("VERIFYING".equals(order.paymentStatus)) {
                Ui.info(this, "Menunggu verifikasi admin",
                        "Data transfer Anda sudah dikirim. Admin akan mencocokkan dengan mutasi yang masuk.");
            } else {
                Ui.ok(this, "Status saat ini: " + Config.orderLabel(order.orderStatus)
                        + ". Kirim data transfer lalu tunggu verifikasi admin.");
            }
        });
        findViewById(R.id.pay_confirm).setOnClickListener(v -> askTransferData());
    }

    /**
     * Meminta pembeli mengisi data transfer.
     *
     * Isian ini yang membuat admin dapat mencocokkan pesanan dengan mutasi yang
     * masuk. Validasi dilakukan sebelum disimpan agar pesanan tidak menunggu
     * verifikasi dengan keterangan yang tidak lengkap.
     */
    private void askTransferData() {
        if (order == null) return;
        if ("PAID".equals(order.paymentStatus)) {
            Ui.ok(this, "Pembayaran sudah diverifikasi");
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(30, 10, 30, 0);
        final EditText sender = new EditText(this);
        sender.setHint("Nama pengirim sesuai rekening");
        sender.setText(order.buyerName.isEmpty() ? (user == null ? "" : user.name) : order.buyerName);
        final EditText amount = new EditText(this);
        amount.setHint("Nominal yang ditransfer (angka)");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (order.paidAmount > 0) amount.setText(String.valueOf(order.paidAmount));
        final EditText from = new EditText(this);
        from.setHint("Bank / e-wallet pengirim (opsional)");
        from.setText(order.paidFrom);
        final EditText note = new EditText(this);
        note.setHint("Catatan (opsional, mis. jam transfer)");
        note.setText(order.paidNote);
        box.addView(sender);
        box.addView(amount);
        box.addView(from);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi transfer")
                .setMessage("Tagihan " + Util.rupiah(order.total)
                        + "\nIsi nominal persis seperti yang Anda transfer agar admin mudah mencocokkan.")
                .setView(new android.widget.ScrollView(this) {{ addView(box); }})
                .setNegativeButton("Batal", null)
                .setPositiveButton("Kirim", (d, w) -> {
                    long amt;
                    try { amt = Long.parseLong(amount.getText().toString().trim()); }
                    catch (Exception e) { Ui.error(this, "Nominal harus berupa angka"); return; }
                    try {
                        order = repo.submitPayment(order.orderId, sender.getText().toString().trim(), amt,
                                from.getText().toString(), note.getText().toString());
                        renderStatus();
                        Ui.info(this, "Data transfer terkirim",
                                "Admin akan memverifikasi pembayaran Anda.\nStatus: MENUNGGU VERIFIKASI");
                    } catch (Repository.RuleException e) {
                        Ui.error(this, e.getMessage());
                    }
                })
                .show();
    }

    @Override protected void onSessionReady(Models.User user) { /* order sudah dimuat */ }

    @Override protected void onResume() {
        super.onResume();
        if (order != null) { order = repo.order(order.orderId); renderStatus(); }
    }

    private void renderStatus() {
        TextView tv = findViewById(R.id.pay_status);
        if (order == null || tv == null) return;
        tv.setText(Config.orderLabel(order.orderStatus) + " · " + Config.paymentLabel(order.paymentStatus));
        tv.setBackgroundResource("PAID".equals(order.paymentStatus)
                ? R.drawable.bg_circle_green : R.drawable.bg_accent_pill);
    }

    /** Menghasilkan QR berukuran px dari payload memakai encoder ZXing. */
    static Bitmap renderQr(String content, int size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) {
                int offset = y * size;
                for (int x = 0; x < size; x++) {
                    px[offset + x] = matrix.get(x, y) ? 0xFF1A241A : 0xFFFFFFFF;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, size, 0, 0, size, size);
            return bmp;
        } catch (Exception e) {
            throw new IllegalStateException("QR gagal dibuat: " + e.getMessage(), e);
        }
    }

    private void shareQr() {
        try {
            ImageView iv = findViewById(R.id.pay_qr);
            iv.setDrawingCacheEnabled(true);
            Bitmap bmp = iv.getDrawingCache() != null
                    ? Bitmap.createBitmap(iv.getDrawingCache())
                    : renderQr(payload, 640);
            iv.setDrawingCacheEnabled(false);

            File dir = new File(getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("folder cache gagal dibuat");
            File f = new File(dir, "qris_" + order.orderNumber + ".png");
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_TEXT, "QRIS " + order.orderNumber + " · " + Util.rupiah(order.total));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Bagikan QRIS"));
        } catch (Exception e) {
            Ui.error(this, "Gagal membagikan QR: " + e.getMessage());
        }
    }
}