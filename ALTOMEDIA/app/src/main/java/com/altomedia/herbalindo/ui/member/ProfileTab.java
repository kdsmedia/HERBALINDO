package com.altomedia.herbalindo.ui.member;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Tab Profil: data akun, referral, riwayat pesanan, dan riwayat poin. */
class ProfileTab {

    private final MemberActivity a;
    private View root;

    ProfileTab(MemberActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.tab_profile, null, false);
            root.findViewById(R.id.prof_logout).setOnClickListener(v -> a.logout());
            root.findViewById(R.id.prof_change_password).setOnClickListener(v -> changePassword());
        }
        return root;
    }

    /** Mengganti password akun. Password lama wajib benar sebelum perubahan disimpan. */
    private void changePassword() {
        Ui.form(a, "Ubah Password",
                new String[]{"Password lama", "Password baru (min. 6 karakter)", "Ulangi password baru"},
                new boolean[]{true, true, true},
                values -> {
                    try {
                        a.repo().changePassword(a.user.userId, values[0], values[1], values[2]);
                        Ui.ok(a, "Password berhasil diubah");
                        return null;
                    } catch (Repository.RuleException e) {
                        return e.getMessage();
                    }
                });
    }

    void refresh() {
        if (root == null) return;
        Models.User u = a.repo().user(a.user.userId);
        a.user = u;

        ((TextView) root.findViewById(R.id.prof_name)).setText(u.name);
        ((TextView) root.findViewById(R.id.prof_contact)).setText(
                u.contact().isEmpty() ? "-" : u.contact());
        ((TextView) root.findViewById(R.id.prof_ref)).setText("REF " + u.referralId);

        ((TextView) root.findViewById(R.id.prof_row_name)).setText(u.name);
        ((TextView) root.findViewById(R.id.prof_row_contact)).setText(u.contact().isEmpty() ? "-" : u.contact());
        ((TextView) root.findViewById(R.id.prof_row_ref)).setText(u.referralId);
        ((TextView) root.findViewById(R.id.prof_row_status)).setText(
                u.status + (u.verified ? " · TERVERIFIKASI" : " · BELUM VERIFIED"));
        ((TextView) root.findViewById(R.id.prof_row_created)).setText(Util.dateOnly(u.createdAt));

        LinearLayout refs = root.findViewById(R.id.prof_referrals);
        refs.removeAllViews();
        java.util.List<Models.Referral> myRefs = a.repo().myReferrals(u.userId);
        if (myRefs.isEmpty()) {
            refs.addView(Rows.info(a, "Belum ada referral", "-"));
        } else {
            for (Models.Referral r : myRefs) {
                Models.User invited = a.repo().user(r.invitedUserId);
                refs.addView(Rows.info(a,
                        (invited == null ? r.invitedUserId : invited.name) + " · " + Util.dateOnly(r.createdAt),
                        r.status + (r.bonusPoints > 0 ? " · +" + Util.num(r.bonusPoints) : "")));
            }
        }

        LinearLayout orders = root.findViewById(R.id.prof_orders);
        orders.removeAllViews();
        java.util.List<Models.Order> myOrders = a.repo().userOrders(u.userId);
        if (myOrders.isEmpty()) {
            orders.addView(Rows.info(a, "Belum ada pesanan", "-"));
        } else {
            for (Models.Order o : myOrders) {
                View row = Rows.info(a,
                        o.orderNumber + " · " + Util.dateTime(o.createdAt),
                        Util.rupiah(o.total) + " · " + com.altomedia.herbalindo.core.Config.orderLabel(o.orderStatus));
                row.setOnClickListener(v -> {
                    android.content.Intent i = new android.content.Intent(a, OrderDetailActivity.class);
                    i.putExtra("orderId", o.orderId);
                    a.startActivity(i);
                });
                orders.addView(row);
            }
        }

        LinearLayout ledger = root.findViewById(R.id.prof_ledger);
        ledger.removeAllViews();
        java.util.List<Models.Ledger> logs = a.repo().ledger(u.userId, 15);
        if (logs.isEmpty()) {
            ledger.addView(Rows.info(a, "Belum ada mutasi poin", "-"));
        } else {
            for (Models.Ledger l : logs) {
                View row = Rows.info(a, l.type + " · " + Util.dateOnly(l.createdAt),
                        (l.amount >= 0 ? "+" : "") + Util.num(l.amount));
                ((TextView) row.findViewById(R.id.info_value)).setTextColor(
                        a.getColor(l.amount >= 0 ? R.color.success : R.color.danger));
                ledger.addView(row);
            }
        }
    }
}