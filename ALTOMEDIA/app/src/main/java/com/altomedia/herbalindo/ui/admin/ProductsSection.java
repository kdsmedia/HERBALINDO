package com.altomedia.herbalindo.ui.admin;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Kelola produk: harga, promo, poin, stok, dan status tayang. */
class ProductsSection {

    private final AdminActivity a;
    private View root;

    ProductsSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.section_list, null, false);
            ((TextView) root.findViewById(R.id.sec_title)).setText("Katalog Produk & Stok");
            root.findViewById(R.id.sec_add).setVisibility(View.GONE);
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    private void render() {
        LinearLayout list = root.findViewById(R.id.sec_list);
        list.removeAllViews();
        for (Models.Product p : a.repo().allProducts()) {
            View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
            ((TextView) card.findViewById(R.id.ac_title)).setText(p.name + (p.active() ? "" : " (nonaktif)"));
            ((TextView) card.findViewById(R.id.ac_sub)).setText(p.sku + " · " + p.category);
            ((TextView) card.findViewById(R.id.ac_badge)).setText("Stok " + p.stock);
            ((TextView) card.findViewById(R.id.ac_badge)).setTextColor(a.getColor(
                    p.stock <= p.minStock ? R.color.danger : R.color.success));
            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    "Harga " + Util.rupiah(p.price)
                            + (p.hasPromo() ? " → promo " + Util.rupiah(p.promoPrice) : "")
                            + " · poin " + Util.num(p.points) + "/unit"
                            + "\nMinimum stok " + p.minStock + " · berat " + p.weight + " g"
                            + "\n" + p.description);

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();

            actions.addView(action("Ubah Produk", R.color.info, v -> editProduct(p)));
            actions.addView(action("Stok +", R.color.success, v -> adjust(p, 1)));
            actions.addView(action("Stok −", R.color.warning, v -> adjust(p, -1)));
            actions.addView(action(p.active() ? "Nonaktifkan" : "Aktifkan", R.color.text_secondary, v -> toggle(p)));
            list.addView(card);
        }
        addStockHistory(list);
    }

    private void addStockHistory(LinearLayout list) {
        View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
        ((TextView) card.findViewById(R.id.ac_title)).setText("Riwayat Pergerakan Stok");
        ((TextView) card.findViewById(R.id.ac_sub)).setText("20 catatan terakhir");
        ((TextView) card.findViewById(R.id.ac_badge)).setText("");
        card.findViewById(R.id.ac_actions).setVisibility(View.GONE);
        StringBuilder sb = new StringBuilder();
        java.util.List<org.json.JSONObject> movements = a.repo().stockMovements();
        int shown = 0;
        for (int i = movements.size() - 1; i >= 0 && shown < 20; i--, shown++) {
            org.json.JSONObject m = movements.get(i);
            sb.append(Util.dateTime(m.optString("createdAt"))).append("\n  ")
                    .append(m.optString("sku")).append(" → ").append(m.optInt("delta") > 0 ? "+" : "")
                    .append(m.optInt("delta")).append(" (sisa ").append(m.optInt("stockAfter")).append(")")
                    .append(" · ").append(m.optString("reason")).append("\n");
        }
        if (shown == 0) sb.append("Belum ada pergerakan stok");
        ((TextView) card.findViewById(R.id.ac_body)).setText(sb.toString());
        list.addView(card);
    }

    private android.widget.Button action(String text, int colorRes, View.OnClickListener l) {
        android.widget.Button b = new android.widget.Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(18, 6, 18, 6);
        b.setTextSize(12);
        b.setBackgroundResource(R.drawable.bg_box);
        b.setTextColor(a.getColor(colorRes));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 8, 4);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private EditText field(LinearLayout box, String hint, String value) {
        EditText et = new EditText(a);
        et.setHint(hint);
        if (value != null) et.setText(value);
        box.addView(et);
        return et;
    }

    private void editProduct(Models.Product p) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        EditText name = field(box, "Nama produk", p.name);
        EditText category = field(box, "Kategori", p.category);
        EditText price = field(box, "Harga (Rp)", String.valueOf(p.price));
        EditText promo = field(box, "Harga promo (0 = tanpa promo)", String.valueOf(p.promoPrice));
        EditText points = field(box, "Poin per unit", String.valueOf(p.points));
        EditText minStock = field(box, "Minimum stok (peringatan)", String.valueOf(p.minStock));
        EditText weight = field(box, "Berat (gram)", String.valueOf(p.weight));
        EditText desc = field(box, "Deskripsi", p.description);
        EditText comp = field(box, "Komposisi", p.composition);
        EditText usage = field(box, "Aturan pakai", p.usage);
        EditText warn = field(box, "Peringatan", p.warning);

        new AlertDialog.Builder(a)
                .setTitle("Ubah " + p.sku)
                .setView(new android.widget.ScrollView(a) {{ addView(box); }})
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    try {
                        Models.Product edited = new Models.Product();
                        edited.productId = p.productId;
                        edited.sku = p.sku;
                        edited.createdAt = p.createdAt;
                        edited.status = p.status;
                        edited.stock = p.stock;
                        edited.name = name.getText().toString().trim();
                        edited.category = category.getText().toString().trim();
                        edited.price = parse(price.getText().toString());
                        edited.promoPrice = parse(promo.getText().toString());
                        edited.points = parse(points.getText().toString());
                        edited.minStock = (int) parse(minStock.getText().toString());
                        edited.weight = (int) parse(weight.getText().toString());
                        edited.description = desc.getText().toString().trim();
                        edited.composition = comp.getText().toString().trim();
                        edited.usage = usage.getText().toString().trim();
                        edited.warning = warn.getText().toString().trim();
                        if (edited.name.length() < 3) { Ui.error(a, "Nama minimal 3 karakter"); return; }
                        if (edited.price <= 0) { Ui.error(a, "Harga harus lebih dari 0"); return; }
                        if (edited.promoPrice > 0 && edited.promoPrice >= edited.price) {
                            Ui.error(a, "Harga promo harus lebih kecil dari harga normal"); return;
                        }
                        a.repo().saveProduct(edited, a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Produk " + edited.sku + " diperbarui");
                    } catch (Exception e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    private long parse(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private void adjust(Models.Product p, int direction) {
        final EditText qty = new EditText(a);
        qty.setHint("Jumlah unit");
        final EditText reason = new EditText(a);
        reason.setHint("Alasan (wajib)");
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        box.addView(qty);
        box.addView(reason);

        new AlertDialog.Builder(a)
                .setTitle((direction > 0 ? "Tambah" : "Kurangi") + " stok " + p.name)
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    long n = parse(qty.getText().toString());
                    String r = reason.getText().toString().trim();
                    if (n <= 0) { Ui.error(a, "Jumlah harus lebih dari 0"); return; }
                    if (r.isEmpty()) { Ui.error(a, "Alasan wajib diisi untuk audit"); return; }
                    try {
                        a.repo().adjustStock(p.productId, (int) (direction * n), r, a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Stok " + p.sku + " diperbarui");
                    } catch (Exception e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    private void toggle(Models.Product p) {
        try {
            Models.Product edited = Models.Product.from(p.toJson().toString());
            edited.setActive(!p.active());
            a.repo().saveProduct(edited, a.user.userId);
            a.refreshActive();
            Ui.ok(a, p.name + (edited.active() ? " diaktifkan" : " dinonaktifkan"));
        } catch (Exception e) {
            Ui.error(a, e.getMessage());
        }
    }
}