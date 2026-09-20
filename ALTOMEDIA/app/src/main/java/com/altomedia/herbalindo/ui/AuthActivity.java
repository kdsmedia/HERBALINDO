package com.altomedia.herbalindo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.admin.AdminActivity;
import com.altomedia.herbalindo.ui.member.MemberActivity;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Satu form untuk Member dan Admin (Bab 2) — role ditentukan oleh data akun,
 * bukan oleh pilihan pengguna, sehingga tidak dapat dieskalasi dari sisi klien.
 */
public class AuthActivity extends BaseActivity {

    private Repository repo;
    private View formLogin, formRegister;
    private TextInputLayout tilLoginId, tilLoginPass, tilRegName, tilRegContact, tilRegPass, tilRegRef;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        if (Session.current(this) != null) { routeByRole(); return; }
        setContentView(R.layout.activity_auth);
        repo = repo();

        formLogin = findViewById(R.id.form_login);
        formRegister = findViewById(R.id.form_register);
        tilLoginId = findViewById(R.id.til_login_id);
        tilLoginPass = findViewById(R.id.til_login_pass);
        tilRegName = findViewById(R.id.til_reg_name);
        tilRegContact = findViewById(R.id.til_reg_contact);
        tilRegPass = findViewById(R.id.til_reg_pass);
        tilRegRef = findViewById(R.id.til_reg_ref);

        findViewById(R.id.tab_login).setOnClickListener(v -> showTab(true));
        findViewById(R.id.tab_register).setOnClickListener(v -> showTab(false));
        findViewById(R.id.btn_login).setOnClickListener(v -> doLogin());
        findViewById(R.id.btn_register).setOnClickListener(v -> doRegister());

        String pending = Session.pendingReferral(this);
        if (pending != null) {
            ((EditText) tilRegRef.getEditText()).setText(pending);
            showTab(false);
        } else {
            showTab(true);
        }
    }

    @Override protected void onSessionReady(Models.User user) { /* belum login saat layar ini tampil */ }

    @Override protected boolean requiresSession() { return false; }

    private void showTab(boolean login) {
        formLogin.setVisibility(login ? View.VISIBLE : View.GONE);
        formRegister.setVisibility(login ? View.GONE : View.VISIBLE);
        findViewById(R.id.tab_login).setSelected(login);
        findViewById(R.id.tab_register).setSelected(!login);
        Ui.hideKeyboard(this);
    }

    private String val(TextInputLayout til) {
        EditText et = til.getEditText();
        return et == null ? "" : et.getText().toString().trim();
    }

    private void doLogin() {
        tilLoginId.setError(null); tilLoginPass.setError(null);
        String id = val(tilLoginId), pass = val(tilLoginPass);
        if (Util.isBlank(id)) { tilLoginId.setError("Wajib diisi"); return; }
        if (Util.isBlank(pass)) { tilLoginPass.setError("Wajib diisi"); return; }
        try {
            Models.User u = repo.login(id, pass);
            repo.log(u.userId, "LOGIN", u.userId, "role=" + u.role);
            Session.set(this, u);
            Ui.hideKeyboard(this);
            routeByRole();
        } catch (Repository.RuleException e) {
            Ui.error(this, e.getMessage());
        }
    }

    private void doRegister() {
        tilRegName.setError(null); tilRegContact.setError(null);
        tilRegPass.setError(null); tilRegRef.setError(null);
        String name = val(tilRegName), contact = val(tilRegContact);
        String pass = val(tilRegPass), ref = val(tilRegRef);
        if (name.length() < 3) { tilRegName.setError("Minimal 3 karakter"); return; }
        if (!Util.isEmail(contact) && !Util.isPhone(contact)) {
            tilRegContact.setError("Masukkan email atau nomor HP (08xxxxxxxxxx)"); return;
        }
        if (pass.length() < 6) { tilRegPass.setError("Minimal 6 karakter"); return; }
        if (!Util.isBlank(ref) && !ref.matches("^\\d{6}$")) {
            tilRegRef.setError("Harus 6 digit angka"); return;
        }
        try {
            Models.User u = repo.register(name, contact, pass, ref);
            repo.log(u.userId, "REGISTER", u.userId,
                    "referralId=" + u.referralId + (Util.isBlank(ref) ? "" : " ref=" + ref));
            Session.clearPendingReferral(this);
            Session.set(this, u);
            Ui.hideKeyboard(this);
            Ui.info(this, "Pendaftaran berhasil",
                    "Referral ID Anda: " + u.referralId
                            + "\n\nReferral ID dibuat otomatis 6 digit dan tidak dapat diubah."
                            + "\nBonus referral berlaku 1 tingkat saja, dibayarkan setelah pesanan pertama Anda terverifikasi.");
            routeByRole();
        } catch (Repository.RuleException e) {
            Ui.error(this, e.getMessage());
        }
    }

    private void routeByRole() {
        Models.User u = Session.current(this);
        if (u == null) return;
        Intent next = "ADMIN".equals(u.role)
                ? new Intent(this, AdminActivity.class)
                : new Intent(this, MemberActivity.class);
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(next);
        finish();
    }
}