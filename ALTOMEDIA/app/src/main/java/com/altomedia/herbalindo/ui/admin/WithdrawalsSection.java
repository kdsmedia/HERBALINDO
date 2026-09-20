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

/** Review pengajuan withdrawal: setujui (transfer manual) atau tolak disertai alasan. */
class WithdrawalsSection {

    private final AdminActivity a;
    private View root;

    WithdrawalsSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.section_list, null, false);
            ((TextView) root.findViewById(R.id.sec_title)).setText("Pengajuan Withdrawal");
            root.findViewById(R.id.sec_add).setVisibility(View.GONE);
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    private void render() {
        LinearLayout list = root.findViewById(R.id.sec_list);
        list.removeAllViews();

        java.util.List<Models.Withdrawal> all = a.repo().allWithdrawals();
        int pending = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            Models.Withdrawal w = all.get(i);
            Models.User owner = a.repo().user(w.userId);
            View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
            ((TextView) card.findViewById(R.id.ac_title)).setText(
                    Util.rupiah(w.amountRupiah) + " · " + (owner == null ? "-" : owner.name));
            ((TextView) card.findViewById(R.id.ac_sub)).setText(
                    w.withdrawalId + "\nDiajukan " + Util.dateTime(w.createdAt));
            TextView badge = card.findViewById(R.id.ac_badge);
            badge.setText(w.status);
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(a, "PAID".equals(w.status) ? R.color.success
                    : "REJECTED".equals(w.status) ? R.color.danger : R.color.warning));
            if ("PENDING".equals(w.status)) pending++;

            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    "Poin dipotong " + Util.num(w.amountPoints)
                            + "\nMetode " + w.method + " → " + w.destination
                            + "\nKontak member: " + (owner == null ? "-" : owner.contact())
                            + "\nSaldo poin saat ini: " + (owner == null ? "-" : Util.num(owner.points))
                            + (Util.isBlank(w.note) ? "" : "\nCatatan: " + w.note));

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();
            if ("PENDING".equals(w.status)) {
                actions.addView(action("Setujui & Tandai Dibayar", R.color.success, v -> decide(w, "PAID")));
                actions.addView(action("Tolak", R.color.danger, v -> decide(w, "REJECTED")));
            } else {
                ((TextView) card.findViewById(R.id.ac_body)).append(
                        "\n\nDiproses " + Util.dateTime(w.processedAt)
                                + (Util.isBlank(w.note) ? "" : " · " + w.note));
            }
            list.addView(card);
        }
        if (all.isEmpty()) {
            list.addView(AdminActivity.row(a, "Belum ada pengajuan withdrawal", "-", R.color.text_secondary));
        }
        ((TextView) root.findViewById(R.id.sec_title)).setText(
                "Pengajuan Withdrawal (" + pending + " menunggu)");
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

    private void decide(Models.Withdrawal w, String status) {
        EditText note = new EditText(a);
        note.setHint("Catatan / bukti transfer (wajib)");
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        box.addView(note);

        boolean approved = "PAID".equals(status);
        new AlertDialog.Builder(a)
                .setTitle(approved ? "Setujui withdrawal" : "Tolak withdrawal")
                .setMessage(approved
                        ? "Pastikan transfer " + Util.rupiah(w.amountRupiah) + " ke " + w.method + " " + w.destination
                          + " sudah dilakukan melalui aplikasi bank/e-wallet Anda. Tindakan ini tercatat pada audit log."
                        : "Poin " + Util.num(w.amountPoints) + " akan dikembalikan ke saldo member. Sertakan alasan penolakan.")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton(approved ? "Ya, sudah ditransfer" : "Tolak", (d, x) -> {
                    String n = note.getText().toString().trim();
                    if (n.isEmpty()) { Ui.error(a, "Catatan wajib diisi"); return; }
                    try {
                        a.repo().processWithdrawal(w.withdrawalId, status, a.user.userId, n);
                        a.refreshActive();
                        Ui.ok(a, w.withdrawalId + " → " + status);
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }
}