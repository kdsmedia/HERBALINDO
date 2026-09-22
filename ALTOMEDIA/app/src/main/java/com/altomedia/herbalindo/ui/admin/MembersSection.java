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
    private EditText search;

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

            // Kolom pencarian sudah tersedia pada tata letak bagian dan
            // ditampilkan hanya untuk daftar member.
            search = root.findViewById(R.id.sec_search);
            search.setVisibility(View.VISIBLE);
            search.setHint("Cari ID / nomor HP / email / nama");
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable s) { render(); }
            });
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    private void render() {
        LinearLayout list = root.findViewById(R.id.sec_list);
        list.removeAllViews();
        String q = search == null || search.getText() == null ? "" : search.getText().toString();
        java.util.List<Models.User> found = a.repo().searchMembers(q);
        for (Models.User u : found) {
            View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
            ((TextView) card.findViewById(R.id.ac_title)).setText(u.name);
            ((TextView) card.findViewById(R.id.ac_sub)).setText(
                    u.contact() + " · REF " + u.referralId + "\nID " + u.userId
                            + " · Daftar " + Util.dateOnly(u.createdAt));
            TextView badge = card.findViewById(R.id.ac_badge);
            badge.setText(u.status + (u.fraudFlag ? " · FRAUD" : ""));
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(a, u.fraudFlag ? R.color.danger
                    : "ACTIVE".equals(u.status) ? R.color.success : R.color.warning));

            Models.User inviter = a.repo().user(u.referredBy);
            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    "Poin " + Util.num(u.points) + " (" + Util.rupiah(a.repo().pointsToRupiah(u.points)) + ")"
                            + " · " + (u.verified ? "terverifikasi" : "belum terverifikasi")
                            + "\nLevel " + com.altomedia.herbalindo.level.Levels.label(
                                    com.altomedia.herbalindo.level.Levels.levelFor(u.xp))
                            + " · " + Util.num(u.xp) + " XP"
                            + "\nDiundang oleh: " + (inviter == null ? "—" : inviter.name + " (" + inviter.referralId + ")")
                            + "\nReferral aktif: " + a.repo().myReferrals(u.userId).size()
                            + " · Pesanan: " + a.repo().userOrders(u.userId).size());

            LinearLayout actions = card.findViewById(R.id.ac_actions);
            actions.removeAllViews();
            actions.addView(action("Edit Data", R.color.info, v -> editMember(u)));
            actions.addView(action("Ubah Saldo", R.color.success, v -> adjustBalance(u)));
            actions.addView(action("Tetapkan Poin", R.color.brand_accent_dark, v -> setPoints(u)));
            actions.addView(action("ACTIVE".equals(u.status) ? "Blokir" : "Aktifkan",
                    R.color.info, v -> toggleStatus(u)));
            actions.addView(action(u.fraudFlag ? "Hapus Fraud" : "Tandai Fraud", R.color.danger, v -> toggleFraud(u)));
            actions.addView(action("Hapus Akun", R.color.danger, v -> deleteMember(u)));
            list.addView(card);
        }
        if (found.isEmpty()) {
            list.addView(AdminActivity.row(a, q.trim().isEmpty()
                    ? "Belum ada member terdaftar"
                    : "Tidak ada member yang cocok dengan \"" + q.trim() + "\"", "-", R.color.text_secondary));
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

    /**
     * Menambah atau mengurangi saldo poin member.
     *
     * Arah ditentukan oleh pilihan admin pada dialog, bukan oleh tombol yang
     * ditekan, supaya tambah dan kurang berada di satu jalur yang sama dan
     * tidak ada nilai arah yang bisa tertukar.
     */
    private void adjustBalance(Models.User u) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);

        android.widget.RadioGroup arah = new android.widget.RadioGroup(a);
        arah.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        android.widget.RadioButton tambah = new android.widget.RadioButton(a);
        tambah.setText("Tambah");
        tambah.setId(android.view.View.generateViewId());
        android.widget.RadioButton kurang = new android.widget.RadioButton(a);
        kurang.setText("Kurangi");
        kurang.setId(android.view.View.generateViewId());
        arah.addView(tambah);
        arah.addView(kurang);
        arah.check(tambah.getId());

        EditText points = new EditText(a);
        points.setHint("Jumlah poin");
        points.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText reason = new EditText(a);
        reason.setHint("Alasan (wajib, tercatat di audit log)");
        box.addView(arah);
        box.addView(points);
        box.addView(reason);

        new AlertDialog.Builder(a)
                .setTitle("Ubah saldo " + u.name)
                .setMessage("Saat ini " + Util.num(u.points) + " poin ("
                        + Util.rupiah(a.repo().pointsToRupiah(u.points)) + ").")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    long n;
                    try { n = Long.parseLong(points.getText().toString().trim()); }
                    catch (Exception e) { Ui.error(a, "Jumlah poin tidak valid"); return; }
                    String r = reason.getText().toString().trim();
                    if (n <= 0) { Ui.error(a, "Jumlah harus lebih dari 0"); return; }
                    if (r.isEmpty()) { Ui.error(a, "Alasan wajib diisi"); return; }
                    long delta = arah.getCheckedRadioButtonId() == kurang.getId() ? -n : n;
                    try {
                        a.repo().adminAdjustBalance(u.userId, delta, r, a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Saldo " + u.name + " " + (delta > 0 ? "ditambah " : "dikurangi ")
                                + Util.num(n) + " poin");
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

    /**
     * Menyunting data pokok member: nama, email, nomor HP, dan kata sandi.
     *
     * Kata sandi dikosongkan secara bawaan — dibiarkan berarti tidak diganti,
     * sehingga admin tidak perlu mengetik ulang sandi lama yang memang tidak
     * dapat dibaca dari basis data.
     */
    private void editMember(Models.User u) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);

        EditText name = field(box, "Nama lengkap", u.name);
        EditText email = field(box, "Email (boleh dikosongkan)", u.email);
        email.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = field(box, "Nomor HP (boleh dikosongkan)", u.phone);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        EditText pass = field(box, "Password baru (kosongkan bila tidak diganti)", "");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Edit data " + u.name)
                .setMessage("ID " + u.userId + " · REF " + u.referralId)
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    try {
                        a.repo().adminUpdateMember(u.userId, name.getText().toString(),
                                email.getText().toString(), phone.getText().toString(),
                                pass.getText().toString(), a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Data " + u.name + " diperbarui");
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
                .show();
    }

    private EditText field(LinearLayout box, String hint, String value) {
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setText(value == null ? "" : value);
        box.addView(et);
        return et;
    }

    /**
     * Menghapus akun member. Alasan wajib diisi karena penghapusan bersifat
     * permanen dan tercatat pada audit log; konfirmasi menyebutkan akibatnya
     * agar tidak dilakukan karena salah tekan.
     */
    private void deleteMember(Models.User u) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        EditText reason = field(box, "Alasan penghapusan (wajib)", "");

        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Hapus akun " + u.name)
                .setMessage("Akun " + u.contact() + " beserta poin, pesanan, referral, "
                        + "dan riwayat withdrawal miliknya akan dihapus permanen. "
                        + "Tindakan ini tidak dapat dibatalkan.")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (d, w) -> {
                    String r = reason.getText().toString().trim();
                    if (r.length() < 3) { Ui.error(a, "Alasan minimal 3 karakter"); return; }
                    try {
                        a.repo().adminDeleteMember(u.userId, r, a.user.userId);
                        a.refreshActive();
                        Ui.ok(a, "Akun " + u.name + " dihapus");
                    } catch (Repository.RuleException e) {
                        Ui.error(a, e.getMessage());
                    }
                })
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