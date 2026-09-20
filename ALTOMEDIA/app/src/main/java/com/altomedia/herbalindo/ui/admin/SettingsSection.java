package com.altomedia.herbalindo.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Pengaturan aturan bisnis: poin, iklan, referral, withdrawal, ongkir. */
class SettingsSection {

    private final AdminActivity a;
    private View root;
    private Models.Settings draft;

    SettingsSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) root = LayoutInflater.from(a).inflate(R.layout.section_settings, null, false);
        return root;
    }

    void refresh() {
        if (root == null) return;
        draft = a.repo().settings();
        set(R.id.set_points_unit, draft.pointsPerUnit);
        set(R.id.set_rupiah_unit, draft.rupiahPerUnit);
        set(R.id.set_checkin, draft.checkinPoints);
        set(R.id.set_ad_points, draft.adPoints);
        set(R.id.set_ad_max, draft.adMaxPerDay);
        set(R.id.set_ref_bonus, draft.referralBonus);
        set(R.id.set_ref_min, draft.referralMinOrder);
        set(R.id.set_min_wd, draft.minWithdrawRupiah);
        set(R.id.set_max_wd, draft.maxWithdrawPerDay);
        set(R.id.set_shipping, draft.shippingFlat);
        set(R.id.set_free_shipping, draft.freeShippingMin);
        ((Switch) root.findViewById(R.id.set_require_ads)).setChecked(draft.requireAdsForWithdraw);
        ((Switch) root.findViewById(R.id.set_purchase_points)).setChecked(draft.purchasePointsEnabled);
        ((Switch) root.findViewById(R.id.set_admob)).setChecked(draft.admobEnabled);

        root.findViewById(R.id.set_save).setOnClickListener(v -> save());
        root.findViewById(R.id.set_reset).setOnClickListener(v -> Ui.confirm(a, "Kembalikan ke default",
                "Seluruh aturan poin, iklan, referral, dan ongkir dikembalikan ke nilai awal spesifikasi.",
                () -> {
                    Models.Settings def = new Models.Settings();
                    try {
                        a.repo().saveSettings(def, a.user.userId);
                        refresh();
                        Ui.ok(a, "Pengaturan dikembalikan ke default");
                    } catch (Exception e) {
                        Ui.error(a, e.getMessage());
                    }
                }));
    }

    private void set(int id, long value) {
        EditText et = root.findViewById(id);
        if (et != null) et.setText(String.valueOf(value));
    }

    private long get(int id) {
        EditText et = root.findViewById(id);
        try { return Long.parseLong(et.getText().toString().trim()); } catch (Exception e) { return -1; }
    }

    private void save() {
        List<String[]> errors = new ArrayList<>();
        long pointsUnit = get(R.id.set_points_unit);
        long rupiahUnit = get(R.id.set_rupiah_unit);
        long checkin = get(R.id.set_checkin);
        long adPoints = get(R.id.set_ad_points);
        long adMax = get(R.id.set_ad_max);
        long refBonus = get(R.id.set_ref_bonus);
        long refMin = get(R.id.set_ref_min);
        long minWd = get(R.id.set_min_wd);
        long maxWd = get(R.id.set_max_wd);
        long shipping = get(R.id.set_shipping);
        long freeShipping = get(R.id.set_free_shipping);

        if (pointsUnit <= 0) errors.add(new String[]{"Konversi poin", "harus lebih dari 0"});
        if (rupiahUnit <= 0) errors.add(new String[]{"Konversi rupiah", "harus lebih dari 0"});
        if (checkin < 0) errors.add(new String[]{"Poin check-in", "tidak boleh negatif"});
        if (adPoints < 0) errors.add(new String[]{"Poin iklan", "tidak boleh negatif"});
        if (adMax <= 0 || adMax > 100) errors.add(new String[]{"Batas iklan harian", "harus antara 1 dan 100"});
        if (refBonus < 0) errors.add(new String[]{"Bonus referral", "tidak boleh negatif"});
        if (refMin < 0) errors.add(new String[]{"Minimum order referral", "tidak boleh negatif"});
        if (minWd < 0) errors.add(new String[]{"Minimum withdrawal", "tidak boleh negatif"});
        if (maxWd <= 0) errors.add(new String[]{"Batas withdrawal harian", "harus minimal 1"});
        if (shipping < 0) errors.add(new String[]{"Ongkir", "tidak boleh negatif"});
        if (freeShipping < 0) errors.add(new String[]{"Gratis ongkir mulai", "tidak boleh negatif"});
        if (freeShipping > 0 && freeShipping < shipping)
            errors.add(new String[]{"Gratis ongkir mulai", "harus ≥ ongkir, jika tidak akan selalu gratis"});

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Perbaiki nilai berikut:\n");
            for (String[] e : errors) sb.append("\n• ").append(e[0]).append(": ").append(e[1]);
            Ui.error(a, sb.toString());
            return;
        }

        Models.Settings s = new Models.Settings();
        s.pointsPerUnit = pointsUnit; s.rupiahPerUnit = rupiahUnit;
        s.checkinPoints = checkin; s.adPoints = adPoints; s.adMaxPerDay = adMax;
        s.referralBonus = refBonus; s.referralMinOrder = refMin;
        s.minWithdrawRupiah = minWd; s.maxWithdrawPerDay = maxWd;
        s.shippingFlat = shipping; s.freeShippingMin = freeShipping;
        s.requireAdsForWithdraw = ((Switch) root.findViewById(R.id.set_require_ads)).isChecked();
        s.purchasePointsEnabled = ((Switch) root.findViewById(R.id.set_purchase_points)).isChecked();
        s.admobEnabled = ((Switch) root.findViewById(R.id.set_admob)).isChecked();

        try {
            a.repo().saveSettings(s, a.user.userId);
            refresh();
            Ui.ok(a, "Pengaturan tersimpan dan langsung berlaku");
        } catch (Exception e) {
            Ui.error(a, "Gagal menyimpan: " + e.getMessage());
        }
    }
}