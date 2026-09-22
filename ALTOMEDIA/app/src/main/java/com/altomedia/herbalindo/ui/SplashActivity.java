package com.altomedia.herbalindo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.ui.admin.AdminActivity;
import com.altomedia.herbalindo.ui.member.MemberActivity;

/** Layar pembuka: menyalurkan deep link referral lalu mengarahkan sesuai role. */
public class SplashActivity extends AppCompatActivity {

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        Insets.enableEdgeToEdge(this);
        setContentView(R.layout.activity_splash);
        Insets.applySystemBars(findViewById(R.id.splash_root));
        handleDeepLink(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String code = intent.getData().getLastPathSegment();
            if (code != null && code.matches("^\\d{6}$")) {
                Session.setPendingReferral(this, code);
                Toast.makeText(this, "Kode referral " + code + " akan dipakai saat mendaftar", Toast.LENGTH_LONG).show();
            }
        }
        // Jeda singkat agar merek terbaca, lalu lanjut ke layar berikutnya.
        findViewById(R.id.splash_root).postDelayed(this::route, 900);
    }

    private void route() {
        if (isFinishing()) return;
        Models.User u = Session.current(this);
        Intent next;
        if (u == null) next = new Intent(this, AuthActivity.class);
        else if ("ADMIN".equals(u.role)) next = new Intent(this, AdminActivity.class);
        else next = new Intent(this, MemberActivity.class);
        startActivity(next);
        finish();
    }
}