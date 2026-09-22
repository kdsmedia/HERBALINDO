package com.altomedia.herbalindo;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.admin.AdminActivity;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class AdminReproTest {
    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
    }

    @Test public void semuaTabAdminDapatDibuka() {
        Models.User admin = Repository.get(ctx).user("USR-ADMIN");
        assertNotNull(admin);
        Session.set(ctx, admin);
        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        try {
            int[] menus = {R.id.adm_nav_dash, R.id.adm_nav_orders, R.id.adm_nav_products,
                    R.id.adm_nav_members, R.id.adm_nav_withdrawals, R.id.adm_nav_settings,
                    R.id.adm_nav_logs};
            for (int id : menus) {
                View item = a.findViewById(id);
                assertNotNull("Baris menu samping tidak ditemukan: " + id, item);
                System.out.println(">>> MENU " + id);
                item.performClick();
                System.out.println(">>> MENU " + id + " OK");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Gagal pada menu panel admin: " + t);
        }
        c.pause().stop().destroy();
    }

    /** Menu samping harus benar-benar terbuka dan tertutup kembali. */
    @Test public void menuSampingMembukaDanMenutup() {
        Models.User admin = Repository.get(ctx).user("USR-ADMIN");
        Session.set(ctx, admin);
        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        androidx.drawerlayout.widget.DrawerLayout drawer = a.findViewById(R.id.adm_drawer);
        assertNotNull("DrawerLayout tidak terpasang", drawer);
        // DrawerLayout baru mengubah statusnya setelah melewati satu putaran
        // tata letak, jadi tata letak dipaksa lebih dulu di lingkungan uji.
        layout(drawer);
        assertFalse("Menu samping harus tertutup saat layar dibuka",
                drawer.isDrawerOpen(androidx.core.view.GravityCompat.START));

        a.findViewById(R.id.adm_menu).performClick();
        layout(drawer);
        assertTrue("Menu samping tidak terbuka saat tombol menu ditekan",
                drawer.isDrawerVisible(androidx.core.view.GravityCompat.START));

        a.findViewById(R.id.adm_nav_members).performClick();
        layout(drawer);
        assertFalse("Menu samping harus tertutup setelah memilih bagian",
                drawer.isDrawerVisible(androidx.core.view.GravityCompat.START));

        a.findViewById(R.id.adm_menu).performClick();
        layout(drawer);
        assertTrue(drawer.isDrawerVisible(androidx.core.view.GravityCompat.START));
        a.onBackPressed();
        layout(drawer);
        assertFalse("Tombol kembali harus menutup menu samping, bukan keluar aplikasi",
                drawer.isDrawerVisible(androidx.core.view.GravityCompat.START));
        assertFalse("Tombol kembali tidak boleh menutup panel admin", a.isFinishing());
        c.pause().stop().destroy();
    }

    private static void layout(android.view.View v) {
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                        org.robolectric.RuntimeEnvironment.getApplication()
                                .getResources().getDisplayMetrics().widthPixels,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        org.robolectric.RuntimeEnvironment.getApplication()
                                .getResources().getDisplayMetrics().heightPixels,
                        android.view.View.MeasureSpec.EXACTLY));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        // DrawerLayout menyelesaikan animasi buka/tutup lewat computeScroll(),
        // yang di perangkat dipanggil saat menggambar. Lingkungan uji tidak
        // menggambar, jadi waktu dan computeScroll digerakkan manual.
        for (int i = 0; i < 60; i++) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(
                    16, java.util.concurrent.TimeUnit.MILLISECONDS);
            v.computeScroll();
        }
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }

    /** Login sebagai admin lewat form yang sama harus membuka panel admin. */
    @Test public void loginAdminDariFormMembukaPanelAdmin() {
        ActivityController<com.altomedia.herbalindo.ui.AuthActivity> c =
                Robolectric.buildActivity(com.altomedia.herbalindo.ui.AuthActivity.class).setup();
        com.altomedia.herbalindo.ui.AuthActivity a = c.get();

        com.google.android.material.textfield.TextInputLayout tilId =
                a.findViewById(R.id.til_login_id);
        com.google.android.material.textfield.TextInputLayout tilPass =
                a.findViewById(R.id.til_login_pass);
        tilId.getEditText().setText(Repository.ADMIN_PHONE);
        tilPass.getEditText().setText(Repository.ADMIN_PASSWORD);
        a.findViewById(R.id.btn_login).performClick();

        System.out.println(">>> finishing=" + a.isFinishing());
        android.content.Intent next =
                org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        System.out.println(">>> next=" + (next == null ? "null" : next.getComponent().getClassName()));
        assertNotNull("Login admin tidak mengarahkan ke layar apa pun", next);
        assertEquals(AdminActivity.class.getName(), next.getComponent().getClassName());
        c.destroy();
    }

    /** Tombol kembali menutup menu samping lebih dulu, bukan langsung keluar. */
    @Test public void tombolKembaliMenutupMenuSamping() {
        Models.User admin = Repository.get(ctx).user("USR-ADMIN");
        Session.set(ctx, admin);
        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        androidx.drawerlayout.widget.DrawerLayout drawer = a.findViewById(R.id.adm_drawer);

        a.findViewById(R.id.adm_menu).performClick();
        layout(drawer);
        assertTrue("Menu samping tidak terbuka",
                drawer.isDrawerVisible(androidx.core.view.GravityCompat.START));

        // Lewat dispatcher, bukan onBackPressed, karena Android 16 tidak lagi
        // memanggil onBackPressed untuk gerakan kembali.
        a.getOnBackPressedDispatcher().onBackPressed();
        layout(drawer);
        assertFalse("Menu samping tidak tertutup oleh tombol kembali",
                drawer.isDrawerVisible(androidx.core.view.GravityCompat.START));
        assertFalse("Layar admin ikut tertutup padahal menu baru ditutup", a.isFinishing());

        a.getOnBackPressedDispatcher().onBackPressed();
        layout(drawer);
        assertTrue("Layar admin tidak tertutup pada tekanan kedua", a.isFinishing());
        c.pause().stop().destroy();
    }
}
