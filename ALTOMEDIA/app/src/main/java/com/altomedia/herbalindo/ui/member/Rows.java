package com.altomedia.herbalindo.ui.member;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.altomedia.herbalindo.R;

/** Baris tampilan ringkas yang dipakai lintas layar member. */
public final class Rows {
    private Rows() {}

    /** Satu baris dengan label kiri dan nilai kanan. */
    public static View info(Activity a, String label, String value) {
        View v = LayoutInflater.from(a).inflate(R.layout.item_info, null, false);
        ((TextView) v.findViewById(R.id.info_label)).setText(label);
        ((TextView) v.findViewById(R.id.info_value)).setText(value);
        return v;
    }

    /** Baris bergaya kartu produk tanpa tombol aksi. */
    public static View card(Activity a, String title, String sub, String right) {
        View v = LayoutInflater.from(a).inflate(R.layout.item_product, null, false);
        ((TextView) v.findViewById(R.id.item_name)).setText(title);
        ((TextView) v.findViewById(R.id.item_sub)).setText(sub);
        ((TextView) v.findViewById(R.id.item_price)).setText(right);
        v.findViewById(R.id.item_action).setVisibility(View.GONE);
        return v;
    }
}