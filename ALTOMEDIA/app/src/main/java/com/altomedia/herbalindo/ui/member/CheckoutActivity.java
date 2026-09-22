package com.altomedia.herbalindo.ui.member;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.ads.AdsManager;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.BaseActivity;

/** Checkout: validasi alamat, ringkasan, lalu membuat order dan menuju QRIS. */
public class CheckoutActivity extends BaseActivity {

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_checkout);
        applyInsets();
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbar_title)).setText("Checkout");
        findViewById(R.id.toolbar_action).setVisibility(View.GONE);

        if (repo.cart().isEmpty()) {
            Ui.error(this, "Keranjang kosong");
            finish();
            return;
        }
        prefill();
        renderSummary();
        findViewById(R.id.co_submit).setOnClickListener(v -> submit());
    }

    @Override protected void onSessionReady(Models.User user) {
        if (user != null && user.lastAddress != null && !user.lastAddress.isEmpty()) prefill();
    }

    private void prefill() {
        Models.User u = user != null ? user : com.altomedia.herbalindo.core.Session.current(this);
        if (u == null) return;
        setText(R.id.co_name, u.name);
        setText(R.id.co_phone, u.phone.isEmpty() ? u.email : u.phone);
        if (!Util.isBlank(u.lastAddress)) {
            String[] parts = u.lastAddress.split("\\|");
            if (parts.length >= 1) setText(R.id.co_address, parts[0]);
            if (parts.length >= 2) setText(R.id.co_city, parts[1]);
            if (parts.length >= 3) setText(R.id.co_postal, parts[2]);
        }
    }

    private void setText(int id, String v) {
        EditText et = findViewById(id);
        if (et != null && (et.getText() == null || et.getText().toString().isEmpty())) et.setText(v);
    }

    private void renderSummary() {
        LinearLayout items = findViewById(R.id.co_items);
        items.removeAllViews();
        for (Repository.CartLine line : repo.cart()) {
            items.addView(Rows.info(this,
                    line.product.name + " × " + line.qty,
                    Util.rupiah(line.subtotal())));
        }
        ((TextView) findViewById(R.id.co_subtotal)).setText(Util.rupiah(repo.cartSubtotal()));
        ((TextView) findViewById(R.id.co_shipping)).setText(
                repo.cartShipping() == 0 ? "GRATIS" : Util.rupiah(repo.cartShipping()));
        ((TextView) findViewById(R.id.co_total)).setText(Util.rupiah(repo.cartTotal()));
        ((TextView) findViewById(R.id.co_points)).setText(
                "Poin yang akan diperoleh setelah pembayaran terverifikasi: +" + Util.num(repo.cartPoints()));
    }

    private String val(int id) {
        EditText et = findViewById(id);
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void submit() {
        String name = val(R.id.co_name);
        String phone = val(R.id.co_phone);
        String address = val(R.id.co_address);
        String city = val(R.id.co_city);
        String postal = val(R.id.co_postal);
        String note = val(R.id.co_note);

        if (name.length() < 3) { Ui.error(this, "Nama penerima minimal 3 karakter"); return; }
        if (!Util.isPhone(phone) && !Util.isEmail(phone)) { Ui.error(this, "Nomor HP tidak valid"); return; }
        if (address.length() < 8) { Ui.error(this, "Alamat terlalu singkat"); return; }
        if (city.length() < 3) { Ui.error(this, "Kota wajib diisi"); return; }
        if (!postal.isEmpty() && !postal.matches("^\\d{4,6}$")) { Ui.error(this, "Kode pos tidak valid"); return; }

        try {
            Models.Order order = repo.createOrder(user, name, phone, address, city, postal, note);
            repo.log(user.userId, "ORDER_CREATE", order.orderNumber, order.items.size() + " item");
            Ui.hideKeyboard(this);
            Intent i = new Intent(this, PaymentActivity.class);
            i.putExtra("orderId", order.orderId);
            // Interstitial ditampilkan pada jeda alami setelah pesanan dibuat.
            // Navigasi tetap berjalan bila iklan belum siap atau gagal tayang.
            AdsManager.get(this).showInterstitial(this, () -> {
                startActivity(i);
                finish();
            });
        } catch (Repository.RuleException e) {
            Ui.error(this, e.getMessage());
        }
    }
}