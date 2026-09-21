package com.altomedia.herbalindo.ui.admin;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Kelola member: status akun, penyesuaian saldo, dan penandaan fraud. */
class MembersSection {

    private final AdminActivity a;
    private View root;

    MembersSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.section_list, null, false);
            ((TextView) root.findViewById(R.id.sec_title)).setText("Daftar Member");
            root.findViewById(R.id.sec_add).setVisibility(View.GONE);
            android.widget.Button scan = root.findViewById(R.id.sec_add2);
            scan.setVisibility(View.VISIBLE);
            scan.setText("Cek Fraud");
            scan.setOnClickListener(v -> openFraudReport());
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    private void render() {
        LinearLayout list = root.findViewById(R.id.sec_list);
        list.removeAllViews();
        for (Models.User u : a.repo().members()) {
            View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
            ((TextView) card.findViewById(R.id.ac_title)).setText(u.name);
            ((TextView) card.findViewById(R.id.ac_sub)).setText(
                    u.contact() + " · REF " + u.referralId + "\nDaftar " + Util.dateOnly(u.createdAt));
            TextView badge = card.findViewById(R.id.ac_badge);
            badge.setText(u.status + (u.fraudFlag ? " · FRAUD" : ""));
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(a, u.fraudFlag ? R.color.danger
                    : "ACTIVE".equals(u.status) ? R.color.success : R.color.warning));

            Models.User inviter = a.repo().user(u.referredBy);
            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    "Poin " + Util.num(u.points) + " (" + Util.rupiah(a.repo().pointsToRupiah(u.points)) + ")"
                            + " · " + (u.verified ? "terverifikasi" : "belum terverifikasi")
                            + "\nDiundang oleh: " + (inviter == null ? "—" : inviter.name + " (" + inviter.referralId + ")")
                            + "\nReferral aktif: " + a.repo().myReferrals(u.userId).size()
                            + " · Pesanan: " + a.repo().userOrders(u.userId).size());

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();
            actions.addView(action("+ Saldo", R.color.success, v -> adjustBalance(u, 1)));
            actions.addView(action("− Saldo", R.color.warning, v -> adjustBalance(u, -1)));
            actions.addView(action("Tetapkan Poin", R.color.brand_accent_dark, v -> setPoints(u)));
            actions.addView(action(u.fraudFlag ? "Hapus Fraud" : "Tandai Fraud", R.color.danger, v -> toggleFraud(u)));
            actions.addView(action("ACTIVE".equals(u.status) ? "Suspend" : "Aktifkan",
                    R.color.info, v -> toggleStatus(u)));
            list.addView(card);
        }
        if (a.repo().members().isEmpty()) {
            list.addView(AdminActivity.row(a, "Belum ada member terdaftar", "-", R.color.text_secondary));
        }
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

    private void adjustBalance(Models.User u, int direction) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        EditText points = new EditText(a);
        points.setHint("Jumlah poin");
        EditText reason = new EditText(a);
        reason.setHint("Alasan (wajib, tercatat di audit log)");
        box.addView(points);
        box.addView(reason);

        new AlertDialog.Builder(a)
                .setTitle((direction > 0 ? "Tambah" : "Kurangi") + " poin " + u.name)
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    long n;
                    try { n = Long.parseLong(points.getText().toString().trim()); }
                    catch (Exception e) { Ui.error(a, "Jumlah poin tidak valid"); return; }
                    String r = reason.getText().toString().trim();
                    if (n <= 0) { Ui.error(a, "Jumlah harus lebih dari 0"); return; }
                    if (r.isEmpty()) { Ui.error(a, "Alasan wajib diisi"); return; }
                    try {
                        a.repo().adminAdjustBalance(u.userId, direction * n, r, a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Saldo " + u.name + " disesuaikan");
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    /**
     * Menetapkan saldo poin member ke angka tertentu.
     *
     * Admin memasukkan hasil akhir, bukan selisih, lalu melihat perubahan
     * sebelum dan sesudahnya pada konfirmasi agar tidak salah tetapkan.
     */
    private void setPoints(Models.User u) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        EditText target = new EditText(a);
        target.setHint("Poin akhir yang diinginkan");
        target.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        target.setText(String.valueOf(u.points));
        EditText reason = new EditText(a);
        reason.setHint("Alasan (wajib, tercatat di audit log)");
        box.addView(target);
        box.addView(reason);

        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Tetapkan poin " + u.name)
                .setMessage("Saat ini " + Util.num(u.points) + " poin ("
                        + Util.rupiah(a.repo().pointsToRupiah(u.points)) + ").")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    long n;
                    try { n = Long.parseLong(target.getText().toString().trim()); }
                    catch (Exception e) { Ui.error(a, "Poin harus berupa angka"); return; }
                    String r = reason.getText().toString().trim();
                    if (r.length() < 3) { Ui.error(a, "Alasan minimal 3 karakter"); return; }
                    try {
                        a.repo().adminSetPoints(u.userId, n, r, a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Poin " + u.name + " → " + Util.num(n));
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    /**
     * Menampilkan hasil pemeriksaan anti-fraud Bab 13.4.
     *
     * Temuan yang bersifat memblokir dapat langsung ditandai ke akunnya,
     * sedangkan temuan lain hanya dilaporkan agar admin menilai sendiri.
     */
    private void openFraudReport() {
        java.util.List<Repository.FraudFinding> findings = a.repo().fraudFindings();
        StringBuilder sb = new StringBuilder();
        if (findings.isEmpty()) {
            sb.append("Tidak ada pola mencurigakan yang terdeteksi.");
        } else {
            int blocking = 0;
            for (Repository.FraudFinding f : findings) {
                if (f.blocking) blocking++;
                sb.append(f.blocking ? "[BLOKIR] " : "[PANTAU] ")
                        .append(f.code).append(" — ").append(f.userName).append("\n  ")
                        .append(f.detail).append("\n\n");
            }
            sb.append(findings.size()).append(" temuan, ").append(blocking).append(" bersifat memblokir.");
        }
        android.content.DialogInterface.OnClickListener aksiBlokir = (d, w) -> {
            try {
                int n = a.repo().applyFraudFindings(a.user.userId);
                a.refreshActive();
                Ui.ok(a, n == 0 ? "Tidak ada temuan baru untuk ditandai" : n + " member ditandai fraud");
            } catch (Exception e) {
                Ui.error(a, e.getMessage());
            }
        };
        new android.app.AlertDialog.Builder(a)
                .setTitle("Laporan Anti-Fraud")
                .setMessage(sb.toString())
                .setNeutralButton("Tutup", null)
                .setPositiveButton("Tandai Blokir", aksiBlokir)
                .show();
    }

    private void toggleFraud(Models.User u) {
        try {
            boolean target = !u.fraudFlag;
            a.repo().setFraud(u.userId, target, a.user.userId);
            a.refreshActive();
            Ui.ok(a, u.name + (target ? " ditandai fraud, withdrawal diblokir" : " bebas dari penandaan fraud"));
        } catch (Repository.RuleException e) {
            Ui.error(a, e.getMessage());
        }
    }

    private void toggleStatus(Models.User u) {
        String target = "ACTIVE".equals(u.status) ? "SUSPENDED" : "ACTIVE";
        Ui.confirm(a, "Ubah status akun", u.name + " → " + target
                + (("SUSPENDED".equals(target)) ? "\nAkun tidak dapat login selama suspend." : ""), () -> {
            try {
                a.repo().setUserStatus(u.userId, target, a.user.userId);
                a.refreshActive();
                Ui.ok(a, "Status " + u.name + " → " + target);
            } catch (Repository.RuleException e) {
                Ui.error(a, e.getMessage());
            }
        });
    }
}