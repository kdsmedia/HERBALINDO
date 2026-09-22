package com.altomedia.herbalindo.core;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
        sb.setBackgroundTint(androidx.core.content.ContextCompat.getColor(a, com.altomedia.herbalindo.R.color.danger));
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

    /** Dialog formulir sederhana berisi beberapa kolom masukan (kata sandi dsb). */
    public interface FormHandler {
        /** Mengembalikan pesan kesalahan, atau null apabila data diterima. */
        String onSubmit(String[] values);
    }

    /**
     * Menyambungkan tombol simpan agar validasi yang gagal tidak menutup dialog.
     *
     * Tombol positif pada {@code AlertDialog} menutup dialog lebih dahulu, baru
     * memanggil pendengarnya. Akibatnya, bila validasi menolak isian, dialog
     * sudah telanjur tertutup dan admin menyangka datanya tersimpan. Penanganan
     * manual ini dipakai agar isian yang salah bisa diperbaiki tanpa mengulang.
     * Handler mengembalikan pesan kesalahan, atau {@code null} bila diterima.
     */
    public interface SubmitHandler {
        String onSubmit();
    }

    public static void submit(AlertDialog dialog, SubmitHandler handler) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String err = handler.onSubmit();
            if (err == null) dialog.dismiss();
            else Toast.makeText(dialog.getContext(), err, Toast.LENGTH_LONG).show();
        });
    }

    public static void form(Context ctx, String title, String[] labels, boolean[] secret,
                            FormHandler handler) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText[] inputs = new EditText[labels.length];
        for (int i = 0; i < labels.length; i++) {
            EditText et = new EditText(ctx);
            et.setHint(labels[i]);
            et.setInputType(secret[i]
                    ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    : InputType.TYPE_CLASS_TEXT);
            box.addView(et);
            inputs[i] = et;
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Batal", (d, w) -> d.dismiss())
                .setPositiveButton("Simpan", null)
                .create();
        dialog.show();

        // Ditangani manual agar dialog tidak tertutup ketika validasi gagal.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] values = new String[inputs.length];
            for (int i = 0; i < inputs.length; i++) values[i] = inputs[i].getText().toString();
            String err = handler.onSubmit(values);
            if (err == null) {
                dialog.dismiss();
            } else {
                inputs[0].requestFocus();
                Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
            }
        });
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