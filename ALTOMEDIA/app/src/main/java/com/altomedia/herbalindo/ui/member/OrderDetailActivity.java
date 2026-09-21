package com.altomedia.herbalindo.ui.member;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.ui.BaseActivity;

/** Detail pesanan milik member beserta tombol ke pembayaran QRIS. */
public class OrderDetailActivity extends BaseActivity {

    private Models.Order order;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_order_detail);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbar_title)).setText("Detail Pesanan");
        findViewById(R.id.toolbar_action).setVisibility(View.GONE);

        order = repo.order(getIntent().getStringExtra("orderId"));
        if (order == null) { Ui.error(this, "Pesanan tidak ditemukan"); finish(); return; }
        com.altomedia.herbalindo.data.Models.User me =
                com.altomedia.herbalindo.core.Session.current(this);
        if (me == null) { toAuth(); return; }
        if (!order.userId.equals(me.userId) && !"ADMIN".equals(me.role)) {
            Ui.error(this, "Pesanan ini bukan milik Anda");
            finish();
            return;
        }
        render();

        findViewById(R.id.od_pay).setOnClickListener(v -> {
            Intent i = new Intent(this, PaymentActivity.class);
            i.putExtra("orderId", order.orderId);
            startActivity(i);
        });
    }

    @Override protected void onSessionReady(Models.User user) { /* order sudah dimuat */ }

    @Override protected void onResume() {
        super.onResume();
        if (order != null) { order = repo.order(order.orderId); if (order != null) render(); }
    }

    private void render() {
        TextView status = findViewById(R.id.od_status);
        status.setText(Config.orderLabel(order.orderStatus) + " · " + Config.paymentLabel(order.paymentStatus));
        status.setBackgroundResource("PAID".equals(order.paymentStatus)
                ? R.drawable.bg_circle_green : R.drawable.bg_accent_pill);

        ((TextView) findViewById(R.id.od_number)).setText(order.orderNumber);
        ((TextView) findViewById(R.id.od_created)).setText(Util.dateTime(order.createdAt));
        ((TextView) findViewById(R.id.od_total)).setText(Util.rupiah(order.total));

        LinearLayout items = findViewById(R.id.od_items);
        items.removeAllViews();
        for (Models.OrderItem i : order.items) {
            items.addView(Rows.info(this, i.name + " × " + i.qty, Util.rupiah(i.price * i.qty)));
        }
        items.addView(Rows.info(this, "Subtotal", Util.rupiah(order.subtotal)));
        items.addView(Rows.info(this, "Ongkir",
                order.shippingCost == 0 ? "GRATIS" : Util.rupiah(order.shippingCost)));
        items.addView(Rows.info(this, "Total", Util.rupiah(order.total)));

        LinearLayout ship = findViewById(R.id.od_shipping);
        ship.removeAllViews();
        ship.addView(Rows.info(this, "Penerima", order.shippingName));
        ship.addView(Rows.info(this, "Telepon", order.shippingPhone));
        ship.addView(Rows.info(this, "Alamat", order.shippingAddressText));
        ship.addView(Rows.info(this, "Kurir",
                Util.isBlank(order.shippingCourier) ? "Belum ditentukan" : order.shippingCourier));
        ship.addView(Rows.info(this, "No. Resi",
                Util.isBlank(order.trackingNumber) ? "Belum tersedia" : order.trackingNumber));
        if (!Util.isBlank(order.note)) ship.addView(Rows.info(this, "Catatan", order.note));

        LinearLayout pay = findViewById(R.id.od_payment);
        pay.removeAllViews();
        pay.addView(Rows.info(this, "Status", Config.paymentLabel(order.paymentStatus)));
        if (Util.isBlank(order.buyerName) && order.paidAmount <= 0) {
            pay.addView(Rows.info(this, "Data transfer", "Belum dikirim"));
        } else {
            pay.addView(Rows.info(this, "Nama pengirim",
                    Util.isBlank(order.buyerName) ? "—" : order.buyerName));
            pay.addView(Rows.info(this, "Nominal ditransfer",
                    order.paidAmount > 0 ? Util.rupiah(order.paidAmount) : "—"));
            Boolean cocok = order.paidMatches();
            pay.addView(Rows.info(this, "Kesesuaian", cocok == null ? "Belum dinilai"
                    : cocok ? "SESUAI" : "TIDAK SESUAI"));
        }

        findViewById(R.id.od_pay).setVisibility(
                "PAID".equals(order.paymentStatus) || "REFUNDED".equals(order.paymentStatus)
                        ? View.GONE : View.VISIBLE);
    }
}