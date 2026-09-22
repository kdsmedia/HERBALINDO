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

    /** Popup tampil saat aplikasi dibuka dan berisi judul serta tombol JOIN. */
    @Test public void popupTampilSaatAplikasiDibuka() {
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();

        android.app.Dialog dialog = lastDialog(a);
        assertNotNull("Popup WhatsApp tidak tampil saat aplikasi dibuka", dialog);
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

    /** Popup hanya sekali per proses, bukan setiap layar dibuka. */
    @Test public void popupHanyaSekaliPerProses() {
        ActivityController<SplashActivity> c1 = Robolectric.buildActivity(SplashActivity.class).setup();
        assertNotNull(lastDialog(c1.get()));
        lastDialog(c1.get()).dismiss();
        c1.pause().stop().destroy();

        ActivityController<SplashActivity> c2 = Robolectric.buildActivity(SplashActivity.class).setup();
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

        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();
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

        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
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

        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        assertNull("Popup dibuka memakai tautan tidak sah", lastDialog(c.get()));
        c.pause().stop().destroy();
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

    /** Popup tetap dapat ditampilkan setelah penanda direset (pratinjau admin). */
    @Test public void pratinjauAdminMenampilkanPopup() {
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();
        android.app.Dialog first = lastDialog(a);
        assertNotNull(first);
        first.dismiss();

        WhatsappPromo.preview(a, "https://chat.whatsapp.com/PratinjauAdmin");
        android.app.Dialog second = lastDialog(a);
        assertNotNull("Pratinjau admin tidak menampilkan popup", second);
        assertTrue(second.isShowing());
        second.dismiss();
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
