package com.altomedia.herbalindo.ui.member;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;

/** Tab Home: saldo, ringkasan harian, produk pilihan. */
class HomeTab {

    private final MemberActivity a;
    private View root;

    HomeTab(MemberActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.tab_home, null, false);
            root.findViewById(R.id.quick_checkin).setOnClickListener(v -> quickCheckin());
            root.findViewById(R.id.quick_ads).setOnClickListener(v -> a.goToTab("tasks"));
            root.findViewById(R.id.quick_saldo).setOnClickListener(v -> a.goToTab("balance"));
        }
        return root;
    }

    private void quickCheckin() {
        try {
            a.repo().checkin(a.user.userId);
            a.onSessionReady(a.repo().user(a.user.userId));
            a.show("home");
            com.altomedia.herbalindo.core.Ui.ok(a, "Check-in berhasil, +" + a.repo().settings().checkinPoints + " poin");
        } catch (com.altomedia.herbalindo.data.Repository.RuleException e) {
            com.altomedia.herbalindo.core.Ui.error(a, e.getMessage());
        }
    }

    void refresh() {
        if (root == null) return;
        Models.User u = a.repo().user(a.user.userId);
        a.user = u;

        ((TextView) root.findViewById(R.id.home_saldo)).setText(Util.rupiah(a.repo().pointsToRupiah(u.points)));
        ((TextView) root.findViewById(R.id.home_poin)).setText(Util.num(u.points) + " poin");
        ((TextView) root.findViewById(R.id.home_ref)).setText(
                "Referral ID · " + u.referralId + (u.verified ? "  ·  TERVERIFIKASI" : "  ·  BELUM VERIFIED"));

        Models.DailyTask t = a.repo().todayTask(u.userId);
        ((TextView) root.findViewById(R.id.home_today)).setText(
                "Check-in: " + (t.checkin ? "sudah" : "belum")
                        + " · Iklan: " + t.adsWatched + "/" + a.repo().settings().adMaxPerDay);

        int orders = a.repo().userOrders(u.userId).size();
        int refs = a.repo().myReferrals(u.userId).size();
        ((TextView) root.findViewById(R.id.home_orders)).setText("Pesanan: " + orders + " · Referral: " + refs);

        LinearLayout list = root.findViewById(R.id.home_products);
        list.removeAllViews();
        java.util.List<Models.Product> products = a.repo().activeProducts();
        for (int i = 0; i < Math.min(3, products.size()); i++) {
            Models.Product p = products.get(i);
            View row = Rows.card(a, p.name, p.category + " · " + p.sku, Util.rupiah(p.effectivePrice()));
            row.findViewById(R.id.item_action).setVisibility(View.VISIBLE);
            row.findViewById(R.id.item_action).setEnabled(true);
            ((android.widget.Button) row.findViewById(R.id.item_action)).setText("Lihat");
            row.findViewById(R.id.item_action).setOnClickListener(v -> a.openProduct(p.productId));
            list.addView(row);
        }
    }
}