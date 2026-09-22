package com.altomedia.herbalindo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.BuildConfig;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.member.MemberActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Teks undangan yang dibagikan member harus sesuai naskah pemasaran yang
 * ditetapkan: pembuka, Referral ID, besaran bonus, dan penutup. Pengujian ini
 * menekan tombol bagikan yang sesungguhnya lalu memeriksa Intent yang keluar,
 * sehingga perubahan naskah yang tidak disengaja tertangkap sebelum dirilis.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class ShareInviteTest {

    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
    }

    @Test public void teksUndanganSesuaiNaskah() throws Exception {
        Models.User u = Repository.get(ctx).register("Budi Santoso", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();

        a.findViewById(R.id.nav_tasks).performClick();

        View share = a.findViewById(R.id.task_ref_share);
        assertNotNull("Tombol bagikan undangan tidak ditemukan", share);
        share.performClick();

        Intent chooser = Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull("Tombol bagikan tidak membuka aplikasi berbagi", chooser);

        Intent send = (Intent) chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull("Intent berbagi tidak membawa Intent pengiriman", send);
        assertEquals(Intent.ACTION_SEND, send.getAction());

        String text = send.getStringExtra(Intent.EXTRA_TEXT);
        assertNotNull("Teks undangan kosong", text);

        String expected = "Ayo bergabung di HERBALINDO\n\n\n"
                + "Gunakan Referral ID: " + u.referralId + "\n"
                + "Bonus 5.000 poin\n"
                + "Komisi dan saldo dapat ditarik setiap hari\n\n\n"
                + "Herbal diet alami, poin harian, dan saldo rupiah.\n\n"
                + "Unduh di Play Store:\n"
                + "https://play.google.com/store/apps/details?id=com.altomedia.herbalindo";
        assertEquals(expected, text);

        assertTrue("Referral ID member tidak ikut dibagikan", text.contains(u.referralId));
        assertTrue("Nama member tidak boleh muncul di undangan", !text.contains(u.name));

        // Tautan Play Store harus menunjuk package name aplikasi ini, bukan
        // package lain. Kesalahan di sini membuat calon member tersesat.
        assertTrue("Tautan Play Store tidak ada di teks undangan",
                text.contains(com.altomedia.herbalindo.core.Config.PLAY_STORE_URL));
        assertTrue("Tautan Play Store tidak memakai package name aplikasi",
                com.altomedia.herbalindo.core.Config.PLAY_STORE_URL.endsWith("id=" + BuildConfig.APPLICATION_ID));
        assertEquals("com.altomedia.herbalindo", BuildConfig.APPLICATION_ID);

        c.destroy();
    }
}
