package com.altomedia.herbalindo.core;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;

/** Utilitas tampilan: dialog, snackbar, keyboard, dan penulisan berkas cache. */
public final class Ui {
    private Ui() {}

    public static void toast(Activity a, String msg) {
        if (a == null || a.isFinishing()) return;
        Snackbar.make(a.findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show();
    }

    public static void ok(Activity a, String msg) { toast(a, msg); }

    public static void error(Activity a, String msg) {
        if (a == null || a.isFinishing()) return;
        Snackbar sb = Snackbar.make(a.findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG);
        sb.setBackgroundTint(a.getColor(com.altomedia.herbalindo.R.color.danger));
        sb.show();
    }

    public static void confirm(Context ctx, String title, String message, Runnable onYes) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Batal", (d, w) -> d.dismiss())
                .setPositiveButton("Ya, lanjutkan", (d, w) -> onYes.run())
                .show();
    }

    public static void info(Context ctx, String title, String message) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Mengerti", null)
                .show();
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    public static void setText(View parent, int id, String value) {
        TextView tv = parent.findViewById(id);
        if (tv != null) tv.setText(value == null ? "-" : value);
    }

    /** Menulis berkas ke cache dan mengembalikan File (untuk dibagikan via FileProvider). */
    public static File writeCache(Context ctx, String name, String content) {
        try {
            File dir = new File(ctx.getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cache tidak dapat dibuat");
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes("UTF-8"));
                out.flush();
            }
            return f;
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menulis berkas: " + e.getMessage(), e);
        }
    }
}