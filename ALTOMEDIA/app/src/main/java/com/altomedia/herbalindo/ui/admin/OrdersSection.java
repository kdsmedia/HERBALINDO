package com.altomedia.herbalindo.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Kelola pesanan: verifikasi pembayaran, ubah status, isi resi, refund. */
class OrdersSection {

    private final AdminActivity a;
    private View root;
    private String filter = "ALL";

    private static final String[] FILTERS = {"ALL", "WAITING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "COMPLETED", "CANCELLED", "REFUNDED"};

    OrdersSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.section_orders, null, false);
            Spinner sp = root.findViewById(R.id.ord_filter);
            ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, FILTERS);
            sp.setAdapter(ad);
            sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    filter = FILTERS[pos];
                    render();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
            });
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    private void render() {
        LinearLayout list = root.findViewById(R.id.ord_list);
        list.removeAllViews();
        java.util.List<Models.Order> orders = a.repo().allOrders();
        int shown = 0;
        for (int i = orders.size() - 1; i >= 0; i--) {
            Models.Order o = orders.get(i);
            if (!"ALL".equals(filter) && !filter.equals(o.orderStatus)) continue;
            shown++;
            Models.User buyer = a.repo().user(o.userId);
            View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
            ((TextView) card.findViewById(R.id.ac_title)).setText(o.orderNumber);
            ((TextView) card.findViewById(R.id.ac_sub)).setText(
                    (buyer == null ? "-" : buyer.name + " · " + buyer.contact())
                            + "\n" + Util.dateTime(o.createdAt));
            TextView badge = card.findViewById(R.id.ac_badge);
            badge.setText(Config.orderLabel(o.orderStatus));
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(a, "PAID".equals(o.paymentStatus) ? R.color.success : R.color.warning));

            StringBuilder items = new StringBuilder();
            for (Models.OrderItem it : o.items) items.append("• ").append(it.name).append(" × ").append(it.qty)
                    .append(" = ").append(Util.rupiah(it.price * it.qty)).append("\n");
            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    items.toString() + "\nTotal " + Util.rupiah(o.total)
                            + " · " + Config.paymentLabel(o.paymentStatus)
                            + "\nKirim ke: " + o.shippingName + " · " + o.shippingPhone
                            + "\n" + o.shippingAddressText
                            + (Util.isBlank(o.trackingNumber) ? "" : "\nResi: " + o.shippingCourier + " " + o.trackingNumber));

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();

            if (!"PAID".equals(o.paymentStatus) && !"REFUNDED".equals(o.paymentStatus)) {
                actions.addView(btn(actions, "Verifikasi Bayar", R.color.success, v -> mark(o, "PAID", "Pembayaran diverifikasi admin")));
            }
            if ("PAID".equals(o.orderStatus)) {
                actions.addView(btn(actions, "Proses", R.color.info, v -> mark(o, "PROCESSING", "Pesanan mulai diproses")));
            }
            if ("PROCESSING".equals(o.orderStatus)) {
                actions.addView(btn(actions, "Input Resi & Kirim", R.color.info, v -> askTracking(o)));
            }
            if ("SHIPPED".equals(o.orderStatus)) {
                actions.addView(btn(actions, "Diterima", R.color.success, v -> mark(o, "DELIVERED", "Paket diterima pembeli")));
            }
            if ("DELIVERED".equals(o.orderStatus)) {
                actions.addView(btn(actions, "Selesaikan", R.color.success, v -> mark(o, "COMPLETED", "Pesanan selesai")));
            }
            if (!"REFUNDED".equals(o.orderStatus) && !"CANCELLED".equals(o.orderStatus)) {
                actions.addView(btn(actions, "Refund", R.color.danger, v -> Ui.confirm(a, "Refund pesanan",
                        "Refund " + o.orderNumber + "? Poin belanja dan bonus referral akan ditarik kembali, dan stok dikembalikan.",
                        () -> mark(o, "REFUNDED", "Refund oleh admin"))));
            }
            list.addView(card);
        }
        TextView empty = root.findViewById(R.id.ord_empty);
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        empty.setText("Tidak ada pesanan dengan status " + filter);
    }

    private android.widget.Button btn(LinearLayout parent, String text, int colorRes, View.OnClickListener l) {
        android.widget.Button b = new android.widget.Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(18, 6, 18, 6);
        b.setTextSize(12);
        b.setBackgroundResource(R.drawable.bg_box);
        b.setTextColor(androidx.core.content.ContextCompat.getColor(a, colorRes));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 8, 4);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void mark(Models.Order o, String status, String note) {
        try {
            a.repo().markStatus(o, status, a.user.userId, note);
            a.refreshActive();
            Ui.ok(a, o.orderNumber + " → " + Config.orderLabel(status));
        } catch (Exception e) {
            Ui.error(a, "Gagal mengubah status: " + e.getMessage());
        }
    }

    private void askTracking(Models.Order o) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(30, 10, 30, 0);
        final android.widget.EditText courier = new android.widget.EditText(a);
        courier.setHint("Kurir (contoh: JNE)");
        final android.widget.EditText resi = new android.widget.EditText(a);
        resi.setHint("Nomor resi");
        box.addView(courier);
        box.addView(resi);
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Kirim " + o.orderNumber)
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan & Kirim", (d, w) -> {
                    String c = courier.getText().toString().trim();
                    String r = resi.getText().toString().trim();
                    if (c.isEmpty() || r.isEmpty()) { Ui.error(a, "Kurir dan resi wajib diisi"); return; }
                    try {
                        o.shippingCourier = c;
                        o.trackingNumber = r;
                        a.repo().saveOrder(o);
                        a.repo().markStatus(o, "SHIPPED", a.user.userId, "Resi " + c + " " + r);
                        a.refreshActive();
                        Ui.ok(a, "Resi tersimpan, status DIKIRIM");
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }
}