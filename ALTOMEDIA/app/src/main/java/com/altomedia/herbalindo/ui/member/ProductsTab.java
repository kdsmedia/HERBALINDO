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

    private final MemberActivity a;
    private View root;
    private String query = "";

    ProductsTab(MemberActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.tab_products, null, false);
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

    private void render() {
        LinearLayout list = root.findViewById(R.id.prod_list);
        TextView empty = root.findViewById(R.id.prod_empty);
        list.removeAllViews();

        int shown = 0;
        for (Models.Product p : a.repo().activeProducts()) {
            if (!query.isEmpty()
                    && !p.name.toLowerCase(java.util.Locale.US).contains(query)
                    && !p.category.toLowerCase(java.util.Locale.US).contains(query)
                    && !p.sku.toLowerCase(java.util.Locale.US).contains(query)) continue;
            shown++;
            View row = LayoutInflater.from(a).inflate(R.layout.item_product, null, false);
            ((TextView) row.findViewById(R.id.item_name)).setText(p.name);
            String priceText = Util.rupiah(p.effectivePrice());
            String sub = p.category + " · " + p.sku + " · stok " + p.stock;
            if (p.hasPromo()) sub = "PROMO · " + sub;
            ((TextView) row.findViewById(R.id.item_sub)).setText(sub);
            ((TextView) row.findViewById(R.id.item_price)).setText(priceText);
            Button act = row.findViewById(R.id.item_action);
            act.setText("+ Keranjang");
            act.setEnabled(p.stock > 0);
            if (p.stock <= 0) { act.setText("Habis"); }
            act.setOnClickListener(v -> addToCart(p));
            row.setOnClickListener(v -> a.openProduct(p.productId));
            list.addView(row);
        }
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        empty.setText(query.isEmpty() ? "Belum ada produk" : "Tidak ada produk cocok dengan \"" + query + "\"");
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