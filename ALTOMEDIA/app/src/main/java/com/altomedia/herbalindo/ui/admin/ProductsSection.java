package com.altomedia.herbalindo.ui.admin;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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
            Button add = root.findViewById(R.id.sec_add);
            add.setVisibility(View.VISIBLE);
            add.setText("Tambah Produk");
            add.setOnClickListener(v -> productForm(null));

            Button category = root.findViewById(R.id.sec_add2);
            category.setVisibility(View.VISIBLE);
            category.setOnClickListener(v -> addCategory());
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
            ((TextView) card.findViewById(R.id.ac_badge)).setTextColor(androidx.core.content.ContextCompat.getColor(a, 
                    p.stock <= p.minStock ? R.color.danger : R.color.success));
            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    "Harga " + Util.rupiah(p.price)
                            + (p.hasPromo() ? " → promo " + Util.rupiah(p.promoPrice) + p.promoPeriodLabel()
                                            : (p.promoPrice > 0 ? " (promo tidak aktif" + p.promoPeriodLabel() + ")" : ""))
                            + " · poin " + Util.num(p.points) + "/unit"
                            + "\nMinimum stok " + p.minStock + " · berat " + p.weight + " g"
                            + "\n" + p.description);

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();

            actions.addView(action("Ubah Produk", R.color.info, v -> productForm(p)));
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
        // Daftar sudah terbaru lebih dahulu; ambil 20 pertama.
        for (int i = 0; i < movements.size() && shown < 20; i++, shown++) {
            org.json.JSONObject m = movements.get(i);
            sb.append(Util.dateTime(m.optString("createdAt"))).append("\n  ")
                    .append(m.optString("sku", m.optString("productId"))).append(" → ")
                    .append(m.optInt("delta") > 0 ? "+" : "")
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
        b.setTextColor(androidx.core.content.ContextCompat.getColor(a, colorRes));
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

    /**
     * Form tambah atau ubah produk.
     *
     * {@code null} berarti produk baru; selain itu form memuat nilai produk
     * yang sedang diubah. Semua field yang diminta admin tersedia di sini:
     * URL gambar, nama, harga, komisi poin, deskripsi, dan stok.
     */
    private void productForm(Models.Product existing) {
        boolean isNew = existing == null;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        EditText name = field(box, "Nama produk", isNew ? "" : existing.name);
        EditText imageUrl = field(box, "URL gambar produk", isNew ? "" : existing.imageUrl);
        EditText price = field(box, "Harga produk (Rp)", isNew ? "" : String.valueOf(existing.price));
        EditText points = field(box, "Komisi poin per unit", isNew ? "" : String.valueOf(existing.points));
        EditText desc = field(box, "Deskripsi produk", isNew ? "" : existing.description);
        EditText stockField = field(box, "Stok tersedia", isNew ? "" : String.valueOf(existing.stock));
        EditText skuField = field(box, "Kode SKU (mis. HBA-004)", isNew ? "" : existing.sku);
        skuField.setEnabled(isNew);
        EditText category = field(box, "Kategori", isNew ? "Herbal Diet" : existing.category);
        EditText promo = field(box, "Harga promo (0 = tanpa promo)", isNew ? "0" : String.valueOf(existing.promoPrice));
        EditText promoStart = field(box, "Promo mulai (yyyy-MM-dd, kosong = bebas)",
                isNew ? "" : existing.promoStart);
        EditText promoEnd = field(box, "Promo berakhir (yyyy-MM-dd, kosong = bebas)",
                isNew ? "" : existing.promoEnd);
        android.widget.CheckBox promoOn = new android.widget.CheckBox(a);
        promoOn.setText("Promo aktif");
        promoOn.setChecked(isNew || existing.promoActive);
        box.addView(promoOn);
        EditText minStock = field(box, "Minimum stok (peringatan)", isNew ? "5" : String.valueOf(existing.minStock));
        EditText weight = field(box, "Berat (gram)", isNew ? "100" : String.valueOf(existing.weight));
        EditText comp = field(box, "Komposisi", isNew ? "" : existing.composition);
        EditText usage = field(box, "Aturan pakai", isNew ? "" : existing.usage);
        EditText warn = field(box, "Peringatan", isNew ? "" : existing.warning);

        new AlertDialog.Builder(a)
                .setTitle(isNew ? "Tambah produk" : "Ubah " + existing.sku)
                .setView(new android.widget.ScrollView(a) {{ addView(box); }})
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    try {
                        Models.Product edited = new Models.Product();
                        edited.sku = skuField.getText().toString().trim().toUpperCase(java.util.Locale.US);
                        edited.productId = isNew ? "PRD-" + edited.sku : existing.productId;
                        edited.createdAt = isNew ? null : existing.createdAt;
                        edited.status = isNew ? "ACTIVE" : existing.status;
                        edited.name = name.getText().toString().trim();
                        edited.imageUrl = imageUrl.getText().toString().trim();
                        edited.category = category.getText().toString().trim();
                        edited.price = parse(price.getText().toString());
                        edited.promoPrice = parse(promo.getText().toString());
                        edited.promoStart = promoStart.getText().toString().trim();
                        edited.promoEnd = promoEnd.getText().toString().trim();
                        edited.promoActive = promoOn.isChecked();
                        edited.points = parse(points.getText().toString());
                        edited.minStock = (int) parse(minStock.getText().toString());
                        edited.weight = (int) parse(weight.getText().toString());
                        edited.description = desc.getText().toString().trim();
                        edited.composition = comp.getText().toString().trim();
                        edited.usage = usage.getText().toString().trim();
                        edited.warning = warn.getText().toString().trim();
                        long newStock = parse(stockField.getText().toString());

                        String problem = validate(edited, newStock, isNew);
                        if (problem != null) { Ui.error(a, problem); return; }

                        if (isNew) {
                            // Stok sengaja dimulai dari nol, lalu ditambah lewat
                            // adjustStock agar jumlah awal ikut tercatat sebagai
                            // pergerakan stok, bukan diam-diam terisi.
                            edited.stock = 0;
                            a.repo().saveProduct(edited, a.user.userId);
                            if (newStock > 0) a.repo().adjustStock(edited.productId, (int) newStock,
                                    "Stok awal produk baru", a.user.userId);
                            Ui.ok(a, "Produk " + edited.sku + " ditambahkan");
                        } else {
                            // Stok lama dibiarkan, lalu selisihnya dicatat sebagai
                            // pergerakan stok agar riwayatnya tetap utuh.
                            edited.stock = existing.stock;
                            a.repo().saveProduct(edited, a.user.userId);
                            if (newStock != existing.stock) {
                                a.repo().adjustStock(edited.productId, (int) (newStock - existing.stock),
                                        "Penyesuaian stok saat ubah produk", a.user.userId);
                            }
                            Ui.ok(a, "Produk " + edited.sku + " diperbarui");
                        }
                        a.refreshActive();
                    } catch (Exception e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    /**
     * Menambah kategori produk.
     *
     * Kategori dipakai agar produk mudah dikelompokkan saat katalog bertambah.
     */
    private void addCategory() {
        final EditText input = new EditText(a);
        input.setHint("Nama kategori baru");
        new AlertDialog.Builder(a)
                .setTitle("Tambah Kategori")
                .setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    try {
                        a.repo().saveCategory(input.getText().toString(), a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Kategori ditambahkan");
                    } catch (Exception e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    /**
     * Memeriksa isian form produk. Mengembalikan pesan masalah, atau
     * {@code null} bila seluruh isian sah.
     */
    private String validate(Models.Product p, long stock, boolean isNew) {
        if (isNew && !p.sku.matches("^[A-Z0-9-]{3,20}$"))
            return "Kode SKU hanya huruf/angka/strip, 3-20 karakter";
        if (isNew && a.repo().product(p.productId) != null)
            return "SKU " + p.sku + " sudah dipakai produk lain";
        if (p.name.length() < 3) return "Nama produk minimal 3 karakter";
        if (p.price <= 0) return "Harga produk harus lebih dari 0";
        if (p.points < 0) return "Komisi poin tidak boleh negatif";
        if (p.description.length() < 5) return "Deskripsi produk minimal 5 karakter";
        if (stock < 0) return "Stok tidak boleh negatif";
        if (stock > 1000000) return "Stok terlalu besar (maksimum 1.000.000)";
        if (!Util.isBlank(p.imageUrl) && !Util.isHttpUrl(p.imageUrl))
            return "URL gambar harus diawali http:// atau https://";
        if (p.promoPrice > 0 && p.promoPrice >= p.price)
            return "Harga promo harus lebih kecil dari harga normal";
        if (!p.promoStart.isEmpty() && !isIsoDate(p.promoStart))
            return "Tanggal promo mulai harus berformat yyyy-MM-dd";
        if (!p.promoEnd.isEmpty() && !isIsoDate(p.promoEnd))
            return "Tanggal promo berakhir harus berformat yyyy-MM-dd";
        if (!p.promoStart.isEmpty() && !p.promoEnd.isEmpty() && p.promoEnd.compareTo(p.promoStart) < 0)
            return "Tanggal promo berakhir tidak boleh sebelum tanggal mulai";
        if (Util.isBlank(p.category)) return "Kategori wajib diisi";
        return null;
    }

    /**
     * Memeriksa tanggal berformat {@code yyyy-MM-dd} sekaligus memastikan
     * tanggalnya benar-benar ada (31 Februari ditolak).
     */
    private boolean isIsoDate(String s) {
        if (!s.matches("^\\d{4}-\\d{2}-\\d{2}$")) return false;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            f.setLenient(false);
            f.parse(s);
            return true;
        } catch (java.text.ParseException e) {
            return false;
        }
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