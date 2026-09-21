package com.altomedia.herbalindo.ui.member;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.google.android.material.textfield.TextInputLayout;

/** Tab Saldo: ringkasan, syarat withdrawal yang transparan, dan pengajuan. */
class BalanceTab {

    private final MemberActivity a;
    private View root;

    BalanceTab(MemberActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.tab_balance, null, false);
            EditText amount = root.findViewById(R.id.bal_amount);
            amount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { updateConversion(); }
                @Override public void afterTextChanged(Editable s) { }
            });
            Spinner method = root.findViewById(R.id.bal_method);
            ArrayAdapter<String> ad = new ArrayAdapter<>(a,
                    android.R.layout.simple_spinner_dropdown_item, Config.WITHDRAW_METHODS);
            method.setAdapter(ad);
            method.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { updateDestinationHint(); }
                @Override public void onNothingSelected(AdapterView<?> p) { }
            });
            root.findViewById(R.id.bal_submit).setOnClickListener(v -> submit());
            updateDestinationHint();
        }
        return root;
    }

    /** Petunjuk isian tujuan menyesuaikan metode: dompet digital atau rekening bank. */
    private void updateDestinationHint() {
        TextInputLayout til = root.findViewById(R.id.bal_dest);
        if (til == null) return;
        til.setHint(Config.isEwallet(selectedMethod())
                ? "Nomor HP terdaftar di " + selectedMethod()
                : "Nomor rekening " + selectedMethod());
    }

    private String selectedMethod() {
        Spinner sp = root.findViewById(R.id.bal_method);
        Object item = sp.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private long requestedPoints() {
        EditText et = root.findViewById(R.id.bal_amount);
        String v = et.getText() == null ? "" : et.getText().toString().trim();
        if (v.isEmpty()) return 0;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return -1; }
    }

    private void updateConversion() {
        TextView tv = root.findViewById(R.id.bal_amount_rp);
        long p = requestedPoints();
        if (p <= 0) { tv.setText("Masukkan jumlah poin"); return; }
        tv.setText("≈ " + Util.rupiah(a.repo().pointsToRupiah(p)));
    }

    void refresh() {
        if (root == null) return;
        Models.User u = a.repo().user(a.user.userId);
        a.user = u;
        Models.Settings s = a.repo().settings();

        ((TextView) root.findViewById(R.id.bal_saldo)).setText(Util.rupiah(a.repo().pointsToRupiah(u.points)));
        ((TextView) root.findViewById(R.id.bal_points)).setText(
                Util.num(u.points) + " poin · konversi " + Util.num(s.pointsPerUnit) + " poin = " + Util.rupiah(s.rupiahPerUnit));

        Repository.Eligibility e = a.repo().eligibility(u);
        LinearLayout checks = root.findViewById(R.id.bal_checks);
        checks.removeAllViews();
        for (String[] c : e.checks) {
            View row = LayoutInflater.from(a).inflate(R.layout.item_info, null, false);
            ((TextView) row.findViewById(R.id.info_label)).setText(c[1]);
            TextView value = row.findViewById(R.id.info_value);
            boolean ok = "1".equals(c[0]);
            value.setText(ok ? "OK" : "BELUM");
            value.setTextColor(androidx.core.content.ContextCompat.getColor(a, ok ? R.color.success : R.color.warning));
            checks.addView(row);
        }

        LinearLayout hist = root.findViewById(R.id.bal_history);
        hist.removeAllViews();
        java.util.List<Models.Withdrawal> list = new java.util.ArrayList<>();
        for (Models.Withdrawal w : a.repo().allWithdrawals()) if (u.userId.equals(w.userId)) list.add(w);
        if (list.isEmpty()) {
            View row = LayoutInflater.from(a).inflate(R.layout.item_info, null, false);
            ((TextView) row.findViewById(R.id.info_label)).setText("Belum ada pengajuan withdrawal");
            ((TextView) row.findViewById(R.id.info_value)).setText("-");
            hist.addView(row);
        } else {
            for (int i = list.size() - 1; i >= 0; i--) {
                Models.Withdrawal w = list.get(i);
                View row = LayoutInflater.from(a).inflate(R.layout.item_info, null, false);
                ((TextView) row.findViewById(R.id.info_label)).setText(
                        Util.dateTime(w.createdAt) + " · " + w.method
                                + (Util.isBlank(w.accountName) ? "" : " · " + w.accountName));
                TextView v = row.findViewById(R.id.info_value);
                v.setText(Util.rupiah(w.amountRupiah) + " · " + w.status);
                v.setTextColor(androidx.core.content.ContextCompat.getColor(a, "PAID".equals(w.status) ? R.color.success
                        : "REJECTED".equals(w.status) ? R.color.danger : R.color.warning));
                hist.addView(row);
            }
        }

        updateConversion();
    }

    private void submit() {
        long points = requestedPoints();
        if (points <= 0) { Ui.error(a, "Jumlah poin tidak valid"); return; }
        String method = selectedMethod();
        EditText holderEt = root.findViewById(R.id.bal_holder);
        EditText destEt = root.findViewById(R.id.bal_dest);
        String holder = holderEt.getText() == null ? "" : holderEt.getText().toString().trim();
        String dest = destEt.getText() == null ? "" : destEt.getText().toString().trim();
        if (!Repository.isWithdrawMethod(method)) { Ui.error(a, "Pilih metode pencairan"); return; }
        if (holder.isEmpty()) { Ui.error(a, "Isi nama pemilik rekening"); return; }
        if (dest.isEmpty()) {
            Ui.error(a, Config.isEwallet(method) ? "Isi nomor HP " + method : "Isi nomor rekening " + method);
            return;
        }

        try {
            Models.Withdrawal w = a.repo().requestWithdrawal(a.user, points, method, holder, dest);
            holderEt.setText(""); destEt.setText("");
            ((EditText) root.findViewById(R.id.bal_amount)).setText("");
            refresh();
            a.onSessionReady(a.repo().user(a.user.userId));
            Ui.info(a, "Pengajuan dikirim",
                    "ID: " + w.withdrawalId
                            + "\nMetode: " + w.method + " · " + w.accountName + " · " + w.destination
                            + "\nJumlah: " + Util.rupiah(w.amountRupiah)
                            + "\nStatus: MENUNGGU REVIEW ADMIN"
                            + "\n\nPoin dipotong sementara sampai admin menyetujui. Jika ditolak, poin dikembalikan otomatis.");
        } catch (Repository.RuleException e) {
            Ui.error(a, e.getMessage());
        }
    }
}