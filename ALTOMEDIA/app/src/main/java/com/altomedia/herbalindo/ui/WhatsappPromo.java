package com.altomedia.herbalindo.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/**
 * Popup ajakan bergabung ke grup WhatsApp resmi HERBALINDO.
 *
 * <p>Popup tampil sekali setiap aplikasi dibuka, bukan setiap layar dibuka.
 * Penandanya disimpan pada variabel statis sehingga bertahan selama proses
 * aplikasi hidup dan otomatis kembali bersih ketika proses dimulai ulang.</p>
 *
 * <p>Tautan dibaca dari pengaturan yang dikelola admin. Bila admin
 * mengosongkan atau mematikan tautannya, popup tidak ditampilkan sama sekali —
 * termasuk pada perangkat yang belum pernah menerima pembaruan pengaturan.</p>
 */
public final class WhatsappPromo {

    /** Penanda sekali pakai untuk satu kali hidup proses aplikasi. */
    private static boolean shownThisLaunch = false;

    private WhatsappPromo() { }

    /** Dipakai pengujian agar tiap kasus uji dapat menguji tampilnya popup. */
    public static void resetForTest() { shownThisLaunch = false; }

    static boolean hasShown() { return shownThisLaunch; }

    /**
     * Menampilkan popup bila aturannya terpenuhi.
     *
     * @return {@code true} bila popup benar-benar ditampilkan.
     */
    public static boolean maybeShow(Activity a, Repository repo) {
        if (a == null || a.isFinishing() || shownThisLaunch) return false;
        Models.Settings s = repo == null ? new Models.Settings() : repo.settings();
        if (!s.whatsappPopupEnabled) return false;
        if (!Config.isValidWhatsappUrl(s.whatsappUrl)) return false;
        shownThisLaunch = true;
        show(a, s.whatsappUrl);
        return true;
    }

    /**
     * Pratinjau untuk admin: menampilkan popup memakai tautan yang diberikan
     * tanpa mengubah penanda "sudah tampil" sehingga popup saat aplikasi dibuka
     * tetap berjalan seperti biasa.
     */
    public static void preview(Activity a, String url) {
        if (a == null || a.isFinishing()) return;
        if (!Config.isValidWhatsappUrl(url)) return;
        show(a, url);
    }

    /** Menampilkan popup tanpa memeriksa aturan (dipakai setelah aturan diperiksa). */
    private static void show(Activity a, String url) {
        View content = LayoutInflater.from(a).inflate(R.layout.dialog_whatsapp, null, false);
        ((TextView) content.findViewById(R.id.wa_title)).setText(Config.WHATSAPP_TITLE);

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setView(content)
                .setCancelable(true)
                .create();

        // Latar bawaan dialog diganti gambar banner, jadi latar jendelanya
        // dibersihkan agar sudut membulat pada banner terlihat.
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawableResource(android.R.color.transparent);

        Button join = content.findViewById(R.id.wa_join);
        join.setOnClickListener(v -> {
            open(a, url);
            dialog.dismiss();
        });
        content.findViewById(R.id.wa_later).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        // Jendela baru ada setelah dialog ditampilkan, jadi latar diatur ulang
        // di sini agar tetap transparan pada semua tingkat API.
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setBackgroundDrawableResource(android.R.color.transparent);
            shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Membuka tautan grup. Kegagalan dibuka dengan pesan, bukan berhenti. */
    static void open(Activity a, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.error(a, a.getString(R.string.wa_no_app));
        } catch (Exception e) {
            Ui.error(a, a.getString(R.string.wa_no_app));
        }
    }
}
