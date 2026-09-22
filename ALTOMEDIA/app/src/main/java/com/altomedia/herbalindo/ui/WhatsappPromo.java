package com.altomedia.herbalindo.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
 * <p>Popup muncul satu menit setelah aplikasi dibuka, bukan seketika, agar
 * pengguna sempat melihat layar yang sedang dibuka lebih dulu.</p>
 *
 * <p>Jadwalnya disimpan pada variabel statis sehingga bertahan selama proses
 * aplikasi hidup: begitu dijadwalkan, layar lain tidak menjadwalkan ulang, dan
 * penandanya otomatis bersih ketika proses dimulai ulang. Waktu tunggunya
 * dilekatkan pada layar yang menjadwalkan; bila layar itu sudah tidak aktif
 * saat waktunya tiba — misalnya pengguna sudah berpindah layar — popup dijadwalkan
 * ulang pada layar yang sedang tampil, dan dibatalkan bila aplikasi sudah
 * tidak terlihat sama sekali.</p>
 *
 * <p>Tautan dibaca dari pengaturan yang dikelola admin. Bila admin
 * mengosongkan atau mematikan tautannya, popup tidak ditampilkan sama sekali —
 * termasuk pada perangkat yang belum pernah menerima pembaruan pengaturan.</p>
 */
public final class WhatsappPromo {

    /** Keadaan popup selama satu kali hidup proses aplikasi. */
    private enum State { BELUM, TERJADWAL, SELESAI }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static State state = State.BELUM;
    private static Runnable pending;
    /** Waktu aplikasi dibuka, dipakai agar jeda tetap dihitung dari awal. */
    private static long dibukaPada;
    /** Layar yang sedang tampil, menjadi sasaran popup bila layar penjadwal selesai. */
    private static java.lang.ref.WeakReference<Activity> layarAktif;

    private WhatsappPromo() { }

    /** Dipakai pengujian agar tiap kasus uji dapat menguji tampilnya popup. */
    public static void resetForTest() {
        if (pending != null) HANDLER.removeCallbacks(pending);
        pending = null;
        state = State.BELUM;
        dibukaPada = 0L;
        layarAktif = null;
    }

    /** {@code true} bila popup sudah pernah ditampilkan pada proses ini. */
    public static boolean hasShown() { return state == State.SELESAI; }

    /** Mencatat layar yang sedang tampil sebagai sasaran popup. */
    public static void onActivityResumed(Activity a) {
        layarAktif = new java.lang.ref.WeakReference<>(a);
    }

    /** Melepas catatan layar saat layar itu tidak lagi tampil. */
    public static void onActivityPaused(Activity a) {
        if (layarAktif != null && layarAktif.get() == a) layarAktif = null;
    }

    /**
     * Menjadwalkan popup satu menit sejak aplikasi dibuka.
     *
     * <p>Perlu dipanggil berulang karena layar yang menjadwalkan bisa berpindah —
     * layar pembuka selesai jauh sebelum satu menit berlalu. Waktu aplikasi
     * dibuka dicatat sekali, sehingga jeda tetap dihitung sejak awal walau
     * penjadwalan berpindah layar. Bila waktunya tiba saat layar penjadwal
     * sudah selesai, popup ditampilkan pada layar yang sedang tampil.</p>
     *
     * @return {@code true} bila popup dijadwalkan pada pemanggilan ini.
     */
    public static boolean schedule(Activity a, Repository repo) {
        if (a == null || a.isFinishing()) return false;
        if (state != State.BELUM) return false;
        if (!isDiizinkan(repo)) return false;

        if (dibukaPada == 0L) dibukaPada = android.os.SystemClock.elapsedRealtime();
        long sisa = Config.WHATSAPP_POPUP_DELAY_MS
                - (android.os.SystemClock.elapsedRealtime() - dibukaPada);
        if (sisa < 0L) sisa = 0L;

        state = State.TERJADWAL;
        final Repository r = repo;
        pending = () -> {
            pending = null;
            Activity sasaran = a;
            if (sasaran.isFinishing() || sasaran.isDestroyed()) {
                sasaran = layarAktif == null ? null : layarAktif.get();
            }
            if (sasaran == null || sasaran.isFinishing() || sasaran.isDestroyed()) {
                // Aplikasi tidak sedang terlihat; biarkan layar berikutnya yang
                // menampilkan, dan jedanya sudah terlewati.
                state = State.BELUM;
                return;
            }
            state = State.SELESAI;
            show(sasaran, r.settings().whatsappUrl);
        };
        HANDLER.postDelayed(pending, sisa);
        return true;
    }

    /** Popup ditampilkan hanya bila diaktifkan admin dan tautannya sah. */
    private static boolean isDiizinkan(Repository repo) {
        Models.Settings s = repo == null ? new Models.Settings() : repo.settings();
        return s.whatsappPopupEnabled && Config.isValidWhatsappUrl(s.whatsappUrl);
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
            // Lebar dibatasi agar popup tidak melebar penuh pada layar besar
            // maupun tablet; pada layar sempit nilai ini tetap dipakai apa adanya.
            android.util.DisplayMetrics m = a.getResources().getDisplayMetrics();
            int lebar = (int) (m.widthPixels * 0.88f);
            int batas = (int) (320 * m.density);
            if (lebar > batas) lebar = batas;
            shown.setLayout(lebar, ViewGroup.LayoutParams.WRAP_CONTENT);
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
