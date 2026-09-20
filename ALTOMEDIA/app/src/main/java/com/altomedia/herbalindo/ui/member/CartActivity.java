package com.altomedia.herbalindo.ui.member;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.BaseActivity;

/** Keranjang belanja sebelum checkout. */
public class CartActivity extends BaseActivity {

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_cart);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbar_title)).setText("Keranjang");
        findViewById(R.id.toolbar_action).setVisibility(View.GONE);
        findViewById(R.id.cart_checkout).setOnClickListener(v -> {
            if (repo.cart().isEmpty()) { Ui.error(this, "Keranjang kosong"); return; }
            startActivity(new Intent(this, CheckoutActivity.class));
        });
    }

    @Override protected void onSessionReady(Models.User user) {
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null) render();
    }

    private void render() {
        LinearLayout list = findViewById(R.id.cart_list);
        View empty = findViewById(R.id.cart_empty);
        View footer = findViewById(R.id.cart_footer);
        list.removeAllViews();

        boolean isEmpty = repo.cart().isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        footer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        findViewById(R.id.cart_scroll).setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        for (Repository.CartLine line : new java.util.ArrayList<>(repo.cart())) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_cart, null, false);
            Models.Product p = line.product;
            ((TextView) row.findViewById(R.id.cart_name)).setText(p.name);
            ((TextView) row.findViewById(R.id.cart_price)).setText(
                    Util.rupiah(p.effectivePrice()) + " × " + line.qty + " · +" + Util.num(line.points()) + " poin");
            ((TextView) row.findViewById(R.id.cart_qty)).setText(String.valueOf(line.qty));
            ((TextView) row.findViewById(R.id.cart_line_total)).setText(Util.rupiah(line.subtotal()));

            row.findViewById(R.id.cart_minus).setOnClickListener(v -> change(line.product.productId, line.qty - 1));
            row.findViewById(R.id.cart_plus).setOnClickListener(v -> change(line.product.productId, line.qty + 1));
            row.findViewById(R.id.cart_remove).setOnClickListener(v -> change(line.product.productId, 0));
            list.addView(row);
        }

        ((TextView) findViewById(R.id.cart_subtotal)).setText(Util.rupiah(repo.cartSubtotal()));
        ((TextView) findViewById(R.id.cart_shipping)).setText(
                repo.cartShipping() == 0 ? "GRATIS" : Util.rupiah(repo.cartShipping()));
        ((TextView) findViewById(R.id.cart_total)).setText(Util.rupiah(repo.cartTotal()));
    }

    private void change(String productId, int qty) {
        try {
            repo.cartSet(productId, qty);
            render();
        } catch (Repository.RuleException e) {
            Ui.error(this, e.getMessage());
        }
    }
}