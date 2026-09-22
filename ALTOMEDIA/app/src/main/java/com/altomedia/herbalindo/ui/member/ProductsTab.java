package com.altomedia.herbalindo.ui.member;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;

/** Tab Produk: pencarian, daftar katalog, dan tombol tambah ke keranjang. */
class ProductsTab {

    /** Jumlah kartu per baris pada grid katalog. */
    private static final int COLUMNS = 2;

    private final MemberActivity a;
    private View root;
    private Grid grid;
    private String query = "";

    ProductsTab(MemberActivity a) { this.a = a; }

    /**
     * Pembantu grid dua kolom berbasis LinearLayout.
     *
     * RecyclerView dengan GridLayoutManager tidak dipakai karena daftar produk
     * dibangun ulang setiap pencarian berubah; jumlah barisnya kecil sehingga
     * pembuatan ulang tampilan tetap murah dan tidak menambah ketergantungan.
     */
    private final class Grid {

        private final LinearLayout container;

        Grid(LinearLayout container) { this.container = container; }

        void clear() { container.removeAllViews(); }

        LinearLayout row() {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return row;
        }

        /** Penyeimbang kolom kosong pada baris terakhir agar kartu tidak melebar. */
        View spacer() {
            View v = new View(a);
            v.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            return v;
        }

        /** Kartu produk dengan lebar tepat satu kolom. */
        View card() {
            View v = LayoutInflater.from(a).inflate(R.layout.item_product_card, null, false);
            v.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            return v;
        }
    }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.tab_products, null, false);
            // Baris grid disiapkan sekali; produk diisikan ke kartu dua kolom.
            grid = new Grid(root.findViewById(R.id.prod_list));
            EditText search = root.findViewById(R.id.prod_search);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    query = s.toString().trim().toLowerCase(java.util.Locale.US);
                    render();
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            root.findViewById(R.id.prod_refresh).setOnClickListener(v -> refresh());
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    /**
     * Menampilkan katalog sebagai grid dua kolom, masing-masing berisi empat
     * kartu ringkas. Jumlah baris dihitung dari produk yang lolos pencarian,
     * dan sisa baris terakhir dibiarkan separuh kosong agar kartu tidak
     * melebar mengikuti lebar layar.
     */
    private void render() {
        LinearLayout list = root.findViewById(R.id.prod_list);
        TextView empty = root.findViewById(R.id.prod_empty);
        list.removeAllViews();
        grid.clear();

        java.util.List<Models.Product> shown = new java.util.ArrayList<>();
        for (Models.Product p : a.repo().activeProducts()) {
            if (!query.isEmpty()
                    && !p.name.toLowerCase(java.util.Locale.US).contains(query)
                    && !p.category.toLowerCase(java.util.Locale.US).contains(query)
                    && !p.sku.toLowerCase(java.util.Locale.US).contains(query)) continue;
            shown.add(p);
        }

        for (int i = 0; i < shown.size(); i += COLUMNS) {
            LinearLayout row = grid.row();
            for (int c = 0; c < COLUMNS; c++) {
                int index = i + c;
                if (index >= shown.size()) {
                    row.addView(grid.spacer());
                } else {
                    row.addView(bindCard(shown.get(index)));
                }
            }
            list.addView(row);
        }

        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(query.isEmpty() ? "Belum ada produk" : "Tidak ada produk cocok dengan \"" + query + "\"");
    }

    /** Mengisi satu kartu produk dan memasang aksinya. */
    private View bindCard(Models.Product p) {
        View card = grid.card();
        ((TextView) card.findViewById(R.id.item_name)).setText(p.name);
        ImageLoader.load(card.findViewById(R.id.item_image), p.imageUrl, R.drawable.ic_product_placeholder);
        String sub = p.category + " · stok " + p.stock;
        if (p.hasPromo()) sub = "PROMO · " + sub;
        ((TextView) card.findViewById(R.id.item_sub)).setText(sub);
        ((TextView) card.findViewById(R.id.item_price)).setText(Util.rupiah(p.effectivePrice()));

        Button act = card.findViewById(R.id.item_action);
        act.setEnabled(p.stock > 0);
        if (p.stock <= 0) act.setAlpha(0.4f);
        act.setOnClickListener(v -> addToCart(p));
        card.setOnClickListener(v -> a.openProduct(p.productId));
        return card;
    }

    private void addToCart(Models.Product p) {
        try {
            a.repo().cartAdd(p.productId, 1);
            a.refreshHeader();
            com.altomedia.herbalindo.core.Ui.ok(a, p.name + " ditambahkan (" + a.repo().cart().size() + " item)");
        } catch (com.altomedia.herbalindo.data.Repository.RuleException e) {
            com.altomedia.herbalindo.core.Ui.error(a, e.getMessage());
        }
    }
}