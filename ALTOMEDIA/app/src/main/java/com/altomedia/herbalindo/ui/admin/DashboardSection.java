package com.altomedia.herbalindo.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Ringkasan operasional toko beserta penjualan dan antrean yang perlu ditindak. */
class DashboardSection {

    private final AdminActivity a;
    private View root;

    DashboardSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) root = LayoutInflater.from(a).inflate(R.layout.section_dashboard, null, false);
        return root;
    }

    void refresh() {
        if (root == null) return;
        Repository.Stats s = a.repo().stats();

        ((TextView) root.findViewById(R.id.dash_member)).setText(Util.num(s.totalMember));
        ((TextView) root.findViewById(R.id.dash_member_sub)).setText(
                s.verifiedMember + " verified · " + s.activeMember + " aktif");
        ((TextView) root.findViewById(R.id.dash_orders)).setText(Util.num(s.totalOrders));
        ((TextView) root.findViewById(R.id.dash_orders_sub)).setText(
                s.orderPending + " menunggu diproses");
        ((TextView) root.findViewById(R.id.dash_withdraw)).setText(Util.num(s.withdrawalPending));
        ((TextView) root.findViewById(R.id.dash_withdraw_sub)).setText(
                Util.rupiah(s.withdrawalPendingRupiah) + " tertahan");
        ((TextView) root.findViewById(R.id.dash_points)).setText(Util.num(s.totalPoints));
        ((TextView) root.findViewById(R.id.dash_points_sub)).setText("≈ " + Util.rupiah(s.totalBalance));
        ((TextView) root.findViewById(R.id.dash_products)).setText(Util.num(s.totalProducts));
        ((TextView) root.findViewById(R.id.dash_products_sub)).setText(s.lowStock + " stok menipis");
        ((TextView) root.findViewById(R.id.dash_refs)).setText(Util.num(s.totalReferral));
        ((TextView) root.findViewById(R.id.dash_refs_sub)).setText(s.verifiedReferral + " terverifikasi");

        LinearLayout alerts = root.findViewById(R.id.dash_alerts);
        alerts.removeAllViews();
        boolean any = false;
        if (s.orderPending > 0) {
            alerts.addView(AdminActivity.row(a, "Pesanan menunggu tindakan", String.valueOf(s.orderPending), R.color.warning));
            any = true;
        }
        if (s.withdrawalPending > 0) {
            alerts.addView(AdminActivity.row(a, "Withdrawal menunggu review",
                    Util.rupiah(s.withdrawalPendingRupiah), R.color.warning));
            any = true;
        }
        if (s.lowStock > 0) {
            alerts.addView(AdminActivity.row(a, "Produk berstok rendah", String.valueOf(s.lowStock), R.color.danger));
            any = true;
        }
        if (!any) alerts.addView(AdminActivity.row(a, "Tidak ada antrean mendesak", "Aman", R.color.success));

        LinearLayout recent = root.findViewById(R.id.dash_recent);
        recent.removeAllViews();
        java.util.List<Models.Order> orders = a.repo().allOrders();
        if (orders.isEmpty()) recent.addView(AdminActivity.row(a, "Belum ada pesanan", "-", R.color.text_secondary));
        else {
            int shown = 0;
            for (int i = orders.size() - 1; i >= 0 && shown < 5; i--, shown++) {
                Models.Order o = orders.get(i);
                Models.User buyer = a.repo().user(o.userId);
                recent.addView(AdminActivity.row(a,
                        o.orderNumber + " · " + (buyer == null ? "-" : buyer.name),
                        Util.rupiah(o.total) + " · " + com.altomedia.herbalindo.core.Config.orderLabel(o.orderStatus),
                        "PAID".equals(o.paymentStatus) ? R.color.success : R.color.warning));
            }
        }
    }
}