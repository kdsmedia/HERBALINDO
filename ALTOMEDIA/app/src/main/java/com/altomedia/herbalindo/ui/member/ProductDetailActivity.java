package com.altomedia.herbalindo.ui.member;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.ads.AdsManager;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.BaseActivity;

/** Detail produk: informasi lengkap, pengaturan jumlah, tambah ke keranjang. */
public class ProductDetailActivity extends BaseActivity {

    private Models.Product product;
    private int qty = 1;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_product_detail);
        String id = getIntent().getStringExtra("productId");
        product = repo.product(id);
        if (product == null) { Ui.error(this, "Produk tidak ditemukan"); finish(); return; }

        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbar_title)).setText(product.name);
        ((TextView) findViewById(R.id.toolbar_sub)).setText(product.category + " · " + product.sku + " · stok " + product.stock);
        findViewById(R.id.toolbar_action).setVisibility(View.GONE);

        ((TextView) findViewById(R.id.det_name)).setText(product.name);
        ((TextView) findViewById(R.id.det_meta)).setText("SKU " + product.sku + " · " + product.category
                + " · berat " + product.weight + " g");
        ((TextView) findViewById(R.id.det_price)).setText(Util.rupiah(product.effectivePrice()));
        TextView old = findViewById(R.id.det_price_old);
        if (product.hasPromo()) {
            old.setVisibility(View.VISIBLE);
            old.setText(Util.rupiah(product.price));
            old.setPaintFlags(old.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        ((TextView) findViewById(R.id.det_points)).setText("+" + Util.num(product.points) + " poin per unit");
        ((TextView) findViewById(R.id.det_desc)).setText(product.description);
        ((TextView) findViewById(R.id.det_comp)).setText(product.composition);
        ((TextView) findViewById(R.id.det_usage)).setText(product.usage);
        ((TextView) findViewById(R.id.det_warn)).setText(product.warning);

        findViewById(R.id.det_minus).setOnClickListener(v -> { if (qty > 1) { qty--; renderQty(); } });
        findViewById(R.id.det_plus).setOnClickListener(v -> {
            if (qty < product.stock) { qty++; renderQty(); }
            else Ui.error(this, "Stok tersisa " + product.stock);
        });
        findViewById(R.id.det_add).setOnClickListener(v -> addToCart());
        renderQty();

        AdsManager.get(this).loadBanner(findViewById(R.id.det_banner));
    }

    @Override protected void onSessionReady(Models.User user) { /* data produk statis saat layar dibuka */ }

    private void renderQty() {
        ((TextView) findViewById(R.id.det_qty)).setText(String.valueOf(qty));
        Button add = findViewById(R.id.det_add);
        add.setEnabled(product.stock > 0);
        add.setText(product.stock > 0
                ? "Tambah " + qty + " ke Keranjang · " + Util.rupiah(product.effectivePrice() * qty)
                : "Stok habis");
    }

    private void addToCart() {
        try {
            repo.cartAdd(product.productId, qty);
            Ui.ok(this, qty + " " + product.name + " masuk keranjang");
            finish();
        } catch (Repository.RuleException e) {
            Ui.error(this, e.getMessage());
        }
    }
}