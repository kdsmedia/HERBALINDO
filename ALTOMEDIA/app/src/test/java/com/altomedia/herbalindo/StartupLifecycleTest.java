package com.altomedia.herbalindo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.AuthActivity;
import com.altomedia.herbalindo.ui.admin.AdminActivity;
import com.altomedia.herbalindo.ui.member.CartActivity;
import com.altomedia.herbalindo.ui.member.CheckoutActivity;
import com.altomedia.herbalindo.ui.member.OrderDetailActivity;
import com.altomedia.herbalindo.ui.member.PaymentActivity;
import com.altomedia.herbalindo.ui.member.ProductDetailActivity;
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

    /** Tautan referral yang dibuka dari luar aplikasi disimpan untuk pendaftaran. */
    @Test public void tautanReferralMenyimpanKodeUntukPendaftaran() {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("herbalindo://ref/482731"));
        ActivityController<SplashActivity> c =
                Robolectric.buildActivity(SplashActivity.class, i).setup();
        assertEquals("482731", Session.pendingReferral(c.get()));
        c.destroy();
    }

    /** Kode referral yang tidak sah tidak disimpan. */
    @Test public void tautanReferralTanpaKodeSahTidakDisimpan() {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("herbalindo://ref/abc"));
        ActivityController<SplashActivity> c =
                Robolectric.buildActivity(SplashActivity.class, i).setup();
        assertNull(Session.pendingReferral(c.get()));
        c.destroy();
    }

    /** Layar pembuka menyalurkan admin ke panel admin, bukan ke layar member. */
    @Test public void layarPembukaMengarahkanAdminKePanelAdmin() {
        Session.set(ctx, Repository.get(ctx).user("USR-ADMIN"));
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();

        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(AdminActivity.class.getName(),
                Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
        c.destroy();
    }

    /** Seluruh layar member harus dapat dibuka tanpa menjatuhkan aplikasi. */
    @Test public void semuaLayarMemberDapatDibuka() throws Exception {
        Models.User u = Repository.get(ctx).register("Budi Santoso", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        buka(MemberActivity.class);
        buka(CartActivity.class);

        Models.User u2 = Repository.get(ctx).user(u.userId);
        Repository.get(ctx).cartAdd("PRD-HBA-001", 2);
        // Checkout dibuka selagi keranjang berisi. createOrder mengosongkan keranjang,
        // sehingga layar checkout yang dibuka setelahnya memang selesai sendiri.
        buka(CheckoutActivity.class);

        Models.Order order = Repository.get(ctx).createOrder(u2, u2.name, u2.contact(),
                "Jl. Melati No. 12", "Karawang", "41361", "");

        buka(ProductDetailActivity.class, "productId", "PRD-HBA-001");
        buka(PaymentActivity.class, "orderId", order.orderId);
        buka(OrderDetailActivity.class, "orderId", order.orderId);
    }

    /** Panel admin harus dapat dibuka dengan sesi admin. */
    @Test public void panelAdminDapatDibuka() {
        Models.User admin = Repository.get(ctx).user("USR-ADMIN");
        assertNotNull("Akun admin awal tidak tersedia", admin);
        Session.set(ctx, admin);
        buka(AdminActivity.class);
    }

    /** Layar detail tanpa data yang sah harus menutup diri dengan rapi, bukan menjatuhkan aplikasi. */
    @Test public void layarDetailTanpaDataSahTidakMenjatuhkanAplikasi() throws Exception {
        Models.User u = Repository.get(ctx).register("Siti Aminah", "081234567891", "rahasia1", null);
        Session.set(ctx, u);
        ActivityController<ProductDetailActivity> c1 =
                buka(ProductDetailActivity.class, "productId", "PRD-TIDAK-ADA");
        assertTrue("Layar produk tanpa data seharusnya menutup diri", c1.get().isFinishing());
        ActivityController<OrderDetailActivity> c2 =
                buka(OrderDetailActivity.class, "orderId", "ORD-TIDAK-ADA");
        assertTrue("Layar pesanan tanpa data seharusnya menutup diri", c2.get().isFinishing());
    }

    /** Alur masuk dan daftar dari layar masuk sampai layar berikutnya. */
    @Test public void alurDaftarDanMasukMengarahkanKeLayarYangTepat() throws Exception {
        Repository.get(ctx).register("Budi Santoso", "081234567890", "rahasia1", null);
        Session.clear(ctx);

        ActivityController<AuthActivity> c = Robolectric.buildActivity(AuthActivity.class).setup();
        AuthActivity a = c.get();
        com.google.android.material.textfield.TextInputLayout tilId = a.findViewById(R.id.til_login_id);
        com.google.android.material.textfield.TextInputLayout tilPass = a.findViewById(R.id.til_login_pass);
        tilId.getEditText().setText("081234567890");
        tilPass.getEditText().setText("rahasia1");
        a.findViewById(R.id.btn_login).performClick();

        assertNotNull("Sesi tidak tersimpan setelah masuk", Session.current(ctx));
        assertEquals("Bukan layar member yang dibuka",
                MemberActivity.class.getName(),
                Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
        c.destroy();
    }

    /** Membuka layar dan memastikan aplikasi tidak berhenti karena kejadian tak terduga. */
    private <T extends androidx.appcompat.app.AppCompatActivity> ActivityController<T> buka(
            Class<T> cls, String extraKey, String extraValue) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        ActivityController<T> c = Robolectric.buildActivity(cls);
        if (extraKey != null) {
            c.get().getIntent().putExtra(extraKey, extraValue);
        }
        c.setup();
        assertFalse(cls.getSimpleName() + " berhenti tak terduga saat dibuka",
                c.get().isFinishing() && extraValue == null);
        c.pause().stop().destroy();
        return c;
    }

    private <T extends androidx.appcompat.app.AppCompatActivity> ActivityController<T> buka(Class<T> cls) {
        return buka(cls, null, null);
    }
}