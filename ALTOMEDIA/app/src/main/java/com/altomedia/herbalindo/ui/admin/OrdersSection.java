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
    private static final String[] FILTER_LABELS = {"Semua", "Belum bayar", "Lunas", "Diproses", "Dikirim", "Diterima", "Selesai", "Dibatalkan", "Dikembalikan"};

    OrdersSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.section_orders, null, false);
            Spinner sp = root.findViewById(R.id.ord_filter);
            ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, FILTER_LABELS);
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

    /**
     * Menyusun keterangan pembayaran untuk admin.
     *
     * Bagian terpenting adalah perbandingan nominal yang pembeli nyatakan
     * dengan total pesanan, karena admin perlu memutuskan berdasarkan mutasi
     * yang benar-benar masuk.
     */
    private String paymentInfo(Models.Order o) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPembayaran: ").append(Config.paymentLabel(o.paymentStatus));
        if (Util.isBlank(o.buyerName) && o.paidAmount <= 0) {
            sb.append("\nData transfer: belum diisi pembeli");
            return sb.toString();
        }
        Boolean cocok = o.paidMatches();
        sb.append("\nNama pengirim: ").append(Util.isBlank(o.buyerName) ? "—" : o.buyerName);
        sb.append("\nNominal ditransfer: Rp")
                .append(o.paidAmount > 0 ? Util.num(o.paidAmount) : "—");
        sb.append("\nTagihan: ").append(Util.rupiah(o.total));
        sb.append("\nKesesuaian: ").append(cocok == null ? "belum diisi"
                : cocok ? "SESUAI" : "TIDAK SESUAI (selisih " + Util.rupiah(Math.abs(o.total - o.paidAmount)) + ")");
        if (!Util.isBlank(o.paidFrom)) sb.append("\nDari: ").append(o.paidFrom);
        if (!Util.isBlank(o.paidNote)) sb.append("\nCatatan pembeli: ").append(o.paidNote);
        return sb.toString();
    }

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
                            + "\nAlamat: " + o.shippingAddressText
                            + (Util.isBlank(o.trackingNumber) ? "" : "\nResi: " + o.shippingCourier + " " + o.trackingNumber)
                            + paymentInfo(o));

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();

            if (!"PAID".equals(o.paymentStatus) && !"REFUNDED".equals(o.paymentStatus)) {
                actions.addView(btn(actions, "Verifikasi Bayar", R.color.success, v -> askVerify(o)));
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
        int idx = 0;
        for (int i = 0; i < FILTERS.length; i++) if (FILTERS[i].equals(filter)) idx = i;
        empty.setText("Tidak ada pesanan dengan status " + FILTER_LABELS[idx]);
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

    /**
     * Meminta admin memverifikasi pembayaran.
     *
     * Bila nominal yang dinyatakan pembeli berbeda dari tagihan, perbedaannya
     * ditampilkan lebih dulu agar admin tidak meloloskan pesanan tanpa sadar.
     */
    private void askVerify(Models.Order o) {
        Boolean cocok = o.paidMatches();
        StringBuilder msg = new StringBuilder(o.orderNumber);
        msg.append("\nTagihan: ").append(Util.rupiah(o.total));
        msg.append("\nNama pengirim: ").append(Util.isBlank(o.buyerName) ? "belum diisi" : o.buyerName);
        msg.append("\nNominal ditransfer: ")
                .append(o.paidAmount > 0 ? Util.rupiah(o.paidAmount) : "belum diisi");
        msg.append("\nKesesuaian: ").append(cocok == null ? "belum diisi pembeli"
                : cocok ? "SESUAI" : "TIDAK SESUAI, selisih " + Util.rupiah(Math.abs(o.total - o.paidAmount)));
        msg.append("\n\nVerifikasi hanya setelah dana benar-benar masuk pada rekening/QRIS Anda.");

        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Verifikasi pembayaran")
                .setMessage(msg.toString())
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tandai LUNAS", (d, w) ->
                        mark(o, "PAID", "Pembayaran diverifikasi admin"
                                + (cocok != null && !cocok ? " (nominal beda)" : "")));
        if (cocok == null) {
            b.setNeutralButton("Isi data manual", (d, w) -> askManualPayment(o));
        }
        b.show();
    }

    /** Mengisi atau mengoreksi data transfer atas nama pembeli (mis. transfer tanpa keterangan). */
    private void askManualPayment(Models.Order o) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(30, 10, 30, 0);
        final android.widget.EditText name = new android.widget.EditText(a);
        name.setHint("Nama pengirim");
        name.setText(o.buyerName);
        final android.widget.EditText amount = new android.widget.EditText(a);
        amount.setHint("Nominal ditransfer (angka)");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (o.paidAmount > 0) amount.setText(String.valueOf(o.paidAmount));
        final android.widget.EditText from = new android.widget.EditText(a);
        from.setHint("Bank / e-wallet pengirim (opsional)");
        from.setText(o.paidFrom);
        box.addView(name);
        box.addView(amount);
        box.addView(from);

        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Data transfer " + o.orderNumber)
                .setView(new android.widget.ScrollView(a) {{ addView(box); }})
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    String n = name.getText().toString().trim();
                    long amt;
                    try { amt = Long.parseLong(amount.getText().toString().trim()); }
                    catch (Exception e) { Ui.error(a, "Nominal harus berupa angka"); return; }
                    try {
                        a.repo().submitPayment(o.orderId, n, amt, from.getText().toString(), o.paidNote);
                        a.refreshActive();
                        Ui.ok(a, "Data pembayaran tersimpan");
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
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