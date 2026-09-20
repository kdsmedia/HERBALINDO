package com.altomedia.herbalindo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.AuthActivity;
import com.altomedia.herbalindo.ui.SplashActivity;
import com.altomedia.herbalindo.ui.member.MemberActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

/**
 * Pengujian jalur pembukaan aplikasi pada perangkat sungguhan.
 *
 * Layar yang tampil tanpa login pernah ditutup sendiri oleh pemeriksaan sesi di
 * {@code BaseActivity.onResume}, sehingga aplikasi langsung menutup saat dibuka
 * pada pengguna yang belum masuk. Pengujian ini menjalankan daur hidup Activity
 * yang nyata agar regresi serupa tertangkap sebelum dirilis.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class StartupLifecycleTest {

    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
    }

    /** Tanpa sesi, layar masuk harus tetap tampil — bukan menutup diri. */
    @Test public void layarMasukTetapTerbukaTanpaSesi() {
        ActivityController<AuthActivity> c = Robolectric.buildActivity(AuthActivity.class).setup();
        AuthActivity a = c.get();
        assertFalse("AuthActivity menutup diri saat dibuka tanpa sesi", a.isFinishing());
        assertNotNull("Layout layar masuk tidak terpasang", a.findViewById(R.id.form_login));
        assertNotNull(a.findViewById(R.id.btn_login));
        c.destroy();
    }

    /** Pengguna yang belum masuk diarahkan ke layar masuk, bukan ditinggal di layar kosong. */
    @Test public void layarMemberMengarahkanKeLayarMasukTanpaSesi() {
        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        ShadowActivity shadow = Shadows.shadowOf(a);
        assertEquals("Bukan layar masuk yang dibuka",
                AuthActivity.class.getName(), shadow.getNextStartedActivity().getComponent().getClassName());
        c.destroy();
    }

    /** Setelah login, layar member terbuka normal tanpa pengalihan. */
    @Test public void layarMemberTerbukaSaatSesiAda() throws Exception {
        Models.User u = Repository.get(ctx).register("Budi Santoso", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        assertFalse("Layar member menutup diri padahal sesi aktif", a.isFinishing());
        assertNull("Tidak seharusnya mengalihkan ke layar lain",
                Shadows.shadowOf(a).getNextStartedActivity());
        assertEquals(u.userId, a.user.userId);
        c.destroy();
    }

    /** Layar pembuka mengarahkan ke layar masuk ketika belum ada sesi. */
    @Test public void layarPembukaMengarahKeLayarMasuk() {
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();
        assertFalse(a.isFinishing());

        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        ShadowActivity shadow = Shadows.shadowOf(a);
        assertEquals(AuthActivity.class.getName(),
                shadow.getNextStartedActivity().getComponent().getClassName());
        c.destroy();
    }
}