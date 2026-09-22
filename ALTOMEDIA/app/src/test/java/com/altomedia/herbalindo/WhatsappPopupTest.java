package com.altomedia.herbalindo;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.SplashActivity;
import com.altomedia.herbalindo.ui.WhatsappPromo;
import com.altomedia.herbalindo.ui.member.MemberActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Popup ajakan bergabung ke grup WhatsApp resmi.
 *
 * Yang diuji: popup muncul saat aplikasi dibuka, tombol JOIN mengarah ke
 * tautan yang dikelola admin, dan perubahan pengaturan admin berpengaruh.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class WhatsappPopupTest {
    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
        WhatsappPromo.resetForTest();
    }

    /** Tautan bawaan harus tautan grup yang diminta. */
    @Test public void tautanBawaanSesuaiSpesifikasi() {
        assertEquals("https://chat.whatsapp.com/EpNPDp3LCTo1eFFVeGbKXW",
                com.altomedia.herbalindo.core.Config.DEFAULT_WHATSAPP_URL);
        assertEquals(com.altomedia.herbalindo.core.Config.DEFAULT_WHATSAPP_URL, Repository.get(ctx).settings().whatsappUrl);
    }

    /** Popup muncul satu menit setelah aplikasi dibuka, bukan seketika. */
    @Test public void popupTampilSatuMenitSetelahAplikasiDibuka() {
        ActivityController<MemberActivity> c = launchMember();
        MemberActivity a = c.get();

        assertEquals("Popup tampil terlalu cepat", null, lastDialog(a));

        // Belum genap satu menit: popup tetap belum muncul.
        ShadowLooper.idleMainLooper(59, java.util.concurrent.TimeUnit.SECONDS);
        assertNull("Popup tampil sebelum jeda satu menit", lastDialog(a));

        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog dialog = lastDialog(a);
        assertNotNull("Popup tidak tampil setelah jeda satu menit", dialog);
        assertTrue(dialog.isShowing());

        TextView title = dialog.findViewById(R.id.wa_title);
        assertNotNull(title);
        assertEquals("BERGABUNG WHATSAPP RESMI HERBALINDO", title.getText().toString());
        Button join = dialog.findViewById(R.id.wa_join);
        assertNotNull("Tombol JOIN tidak ada pada popup", join);
        assertNotNull(dialog.findViewById(R.id.wa_later));

        dialog.dismiss();
        c.pause().stop().destroy();
    }

    /** Popup tetap muncul walau layar pembuka sudah selesai jauh sebelumnya. */
    @Test public void popupTetapMunculSetelahLayarPembukaSelesai() {
        ActivityController<SplashActivity> splash =
                Robolectric.buildActivity(SplashActivity.class).setup();
        assertNull("Popup tampil seketika di layar pembuka", lastDialog(splash.get()));
        splash.pause().stop().destroy();

        ActivityController<MemberActivity> c = launchMember();
        MemberActivity a = c.get();
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull("Popup hilang karena layar pembuka sudah selesai", lastDialog(a));
        lastDialog(a).dismiss();
        c.pause().stop().destroy();
    }

    /** Popup hanya sekali per proses, bukan setiap layar dibuka. */
    @Test public void popupHanyaSekaliPerProses() {
        ActivityController<MemberActivity> c1 = launchMember();
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(lastDialog(c1.get()));
        lastDialog(c1.get()).dismiss();
        c1.pause().stop().destroy();

        ActivityController<MemberActivity> c2 = launchMember();
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        assertNull("Popup muncul lagi padahal proses aplikasi belum dimulai ulang",
                lastDialog(c2.get()));
        c2.pause().stop().destroy();
    }

    /** Tombol JOIN membuka tautan yang sedang diatur admin. */
    @Test public void tombolJoinMembukaTautanAdmin() {
        Repository r = Repository.get(ctx);
        Models.Settings s = r.settings();
        s.whatsappUrl = "https://chat.whatsapp.com/GrupBaruSetelahGanti";
        r.saveSettings(s, null);

        ActivityController<MemberActivity> c = launchMember();
        MemberActivity a = c.get();
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog dialog = lastDialog(a);
        assertNotNull(dialog);
        dialog.findViewById(R.id.wa_join).performClick();

        Intent started = org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull("Tombol JOIN tidak membuka tautan apa pun", started);
        assertEquals("https://chat.whatsapp.com/GrupBaruSetelahGanti", started.getDataString());
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        c.pause().stop().destroy();
    }

    /** Admin dapat mematikan popup; popup tidak boleh muncul. */
    @Test public void popupDapatDimatikanAdmin() {
        Repository r = Repository.get(ctx);
        Models.Settings s = r.settings();
        s.whatsappPopupEnabled = false;
        r.saveSettings(s, null);

        ActivityController<MemberActivity> c = launchMember();
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        assertNull("Popup masih tampil padahal dimatikan admin", lastDialog(c.get()));
        c.pause().stop().destroy();
    }

    /** Tautan tidak sah tidak boleh membuka popup. */
    @Test public void tautanTidakSahTidakMenampilkanPopup() {
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl(null));
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl(""));
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("javascript:alert(1)"));
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("intent://evil#Intent;end"));
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("chat.whatsapp.com/abc"));
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("https://nodot"));
        assertFalse(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("https://spasi dalam url.com"));
        assertTrue(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("https://chat.whatsapp.com/EpNPDp3LCTo1eFFVeGbKXW"));
        assertTrue(com.altomedia.herbalindo.core.Config.isValidWhatsappUrl("http://example.com/x"));

        Repository r = Repository.get(ctx);
        Models.Settings s = r.settings();
        s.whatsappUrl = "javascript:alert(1)";
        r.saveSettings(s, null);

        ActivityController<MemberActivity> c = launchMember();
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        assertNull("Popup dibuka memakai tautan tidak sah", lastDialog(c.get()));
        c.pause().stop().destroy();
    }

    /** Layar member dengan sesi aktif, dipakai sebagai layar tempat popup menyusul. */
    private ActivityController<MemberActivity> launchMember() {
        Repository r = Repository.get(ctx);
        Models.User u = r.user("USR-ADMIN");
        Session.set(ctx, u);
        return Robolectric.buildActivity(MemberActivity.class).setup();
    }

    /** Pengaturan WhatsApp bertahan setelah disimpan dan dibaca ulang. */
    @Test public void pengaturanWhatsappTersimpan() {
        Repository r = Repository.get(ctx);
        Models.Settings s = r.settings();
        s.whatsappUrl = "https://chat.whatsapp.com/TersimpanBaik";
        s.whatsappPopupEnabled = false;
        r.saveSettings(s, null);

        Models.Settings again = r.settings();
        assertEquals("https://chat.whatsapp.com/TersimpanBaik", again.whatsappUrl);
        assertFalse(again.whatsappPopupEnabled);
    }

    /** Pratinjau admin menampilkan popup walau jeda satu menit belum lewat. */
    @Test public void pratinjauAdminMenampilkanPopup() {
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();

        WhatsappPromo.preview(a, "https://chat.whatsapp.com/PratinjauAdmin");
        android.app.Dialog shown = lastDialog(a);
        assertNotNull("Pratinjau admin tidak menampilkan popup", shown);
        assertTrue(shown.isShowing());

        // Pratinjau tidak boleh menghabiskan jatah popup saat aplikasi dibuka.
        assertFalse("Pratinjau menandai popup sebagai sudah tampil", WhatsappPromo.hasShown());
        shown.dismiss();
        c.pause().stop().destroy();
    }

    /** Dialog terakhir yang masih hidup pada aktivitas, atau null. */
    private static android.app.Dialog lastDialog(android.app.Activity a) {
        for (android.app.Dialog d : org.robolectric.shadows.ShadowDialog.getShownDialogs()) {
            if (d != null && d.isShowing()) return d;
        }
        return null;
    }
}
