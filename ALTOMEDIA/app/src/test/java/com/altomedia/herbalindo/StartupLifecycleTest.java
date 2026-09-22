package com.altomedia.herbalindo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

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

    /**
     * Tab Tugas Harian harus menampilkan ringkasan yang benar-benar dihitung
     * dari data (Bab 7.5), bukan angka tetap.
     */
    @Test public void tabTugasMenampilkanRingkasanNyata() throws Exception {
        Models.User u = Repository.get(ctx).register("Siti Aminah", "081234567890", "rahasia1", null);
        Models.User teman = Repository.get(ctx).register("Budi Santoso", "081234567891", "rahasia1", u.referralId);
        Session.set(ctx, u);

        Models.Order milikSaya = beliDanBayar(u, 2);
        beliDanBayar(teman, 1);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        ((com.google.android.material.bottomnavigation.BottomNavigationView)
                a.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_tasks);

        android.widget.TextView refStatus = a.findViewById(R.id.task_ref_status);
        android.widget.TextView buyPoints = a.findViewById(R.id.task_buy_points);
        android.widget.TextView buySub = a.findViewById(R.id.task_buy_sub);
        assertNotNull("Kartu referral tidak tampil", refStatus);
        assertEquals("Referral 1 Verified", refStatus.getText().toString());
        assertEquals("+1.000 Poin", buyPoints.getText().toString());
        assertTrue("Total belanja harus muncul pada ringkasan pembelian",
                buySub.getText().toString().contains("total belanja"));
        c.pause().stop().destroy();

        assertNotNull("Data pesanan uji tidak boleh hilang", Repository.get(ctx).order(milikSaya.orderId));
        assertEquals(1, Repository.get(ctx).verifiedReferralCount(u.userId));
    }

    /**
     * Tab Saldo harus dapat dibuka tanpa menjatuhkan aplikasi. Sebelumnya kode
     * mengambil wadah isian tujuan dengan tipe yang salah, sehingga membuka tab
     * ini melempar ClassCastException.
     */
    @Test public void tabSaldoDapatDibukaTanpaCrash() throws Exception {
        Models.User u = Repository.get(ctx).register("Siti Aminah", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        ((com.google.android.material.bottomnavigation.BottomNavigationView)
                a.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_balance);

        android.widget.TextView saldo = a.findViewById(R.id.bal_saldo);
        assertNotNull("Kartu saldo tidak tampil", saldo);
        assertEquals("Tab Saldo harus menjadi tab aktif",
                R.id.nav_balance, ((com.google.android.material.bottomnavigation.BottomNavigationView)
                        a.findViewById(R.id.bottom_nav)).getSelectedItemId());
        // Isian tujuan wajib memiliki petunjuk yang menyesuaikan metode terpilih.
        com.google.android.material.textfield.TextInputLayout label =
                a.findViewById(R.id.bal_dest_label);
        assertNotNull("Label tujuan tidak ditemukan", label);
        assertNotNull("Petunjuk tujuan harus terisi", label.getHint());
        c.pause().stop().destroy();
    }

    /**
     * Katalog produk harus tersusun grid dua kolom. Jumlah baris mengikuti
     * jumlah produk yang lolos saring, dan sel yang tidak terpakai pada baris
     * terakhir harus berupa penyeimbang kosong agar kartu tidak melebar.
     */
    @Test public void katalogProdukTersusunDuaKolom() throws Exception {
        Models.User u = Repository.get(ctx).register("Siti Aminah", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        ((com.google.android.material.bottomnavigation.BottomNavigationView)
                a.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_products);

        android.widget.LinearLayout list = a.findViewById(R.id.prod_list);
        assertNotNull("Grid produk tidak ditemukan", list);

        int totalProduk = Repository.get(ctx).activeProducts().size();
        assertTrue("Data uji harus memiliki produk", totalProduk > 0);

        int expectedRows = (totalProduk + 1) / 2;
        assertEquals("Jumlah baris grid harus mengikuti dua kolom per baris",
                expectedRows, list.getChildCount());

        int kartuDilihat = 0;
        for (int r = 0; r < list.getChildCount(); r++) {
            android.view.ViewGroup row = (android.view.ViewGroup) list.getChildAt(r);
            assertEquals("Setiap baris harus memuat tepat dua sel", 2, row.getChildCount());
            for (int col = 0; col < 2; col++) {
                android.view.View cell = row.getChildAt(col);
                android.widget.LinearLayout.LayoutParams lp =
                        (android.widget.LinearLayout.LayoutParams) cell.getLayoutParams();
                assertEquals("Sel harus menempati satu kolom berbobot", 1f, lp.weight, 0.01f);
                if (cell.findViewById(R.id.item_name) != null) {
                    kartuDilihat++;
                } else if (col == 0) {
                    fail("Sel pertama pada baris harus berupa kartu produk");
                }
            }
        }
        assertEquals("Semua produk harus tergambar pada grid", totalProduk, kartuDilihat);
        c.pause().stop().destroy();
    }

    /**
     * Header harus menyisakan jarak untuk bilah status pada API 30 ke atas,
     * tempat aplikasi menggambar sampai tepi layar. Padding asli dari XML
     * tidak boleh hilang, dan sisipan yang dilaporkan ulang tidak boleh
     * menumpuk.
     */
    @Test public void headerMemberMenyisakanRuangBilahStatus() throws Exception {
        Models.User u = Repository.get(ctx).register("Budi Santoso", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        View header = a.findViewById(R.id.member_header);
        assertNotNull("Header member tidak ditemukan", header);

        int padTop = header.getPaddingTop();
        // Pembanding: padding asli dari XML, dibaca dengan memasang tata letak
        // sekali lagi tanpa penyesuaian sisipan apa pun.
        View polos = android.view.LayoutInflater.from(a)
                .inflate(R.layout.activity_member, null, false);
        int padXml = polos.findViewById(R.id.member_header).getPaddingTop();
        assertEquals("Padding awal header harus sesuai XML", padXml, padTop);

        if (!com.altomedia.herbalindo.ui.Insets.manual()) {
            assertEquals("Di bawah API 30 jarak bilah status diurus sistem, "
                    + "padding tidak boleh ditambah", padXml, padTop);
        } else {
            // Sisipan bilah status dikirim sendiri agar nilainya pasti, karena
            // Robolectric tidak menyediakan jendela nyata.
            int extra = 64;
            header.dispatchApplyWindowInsets(statusBarInset(extra));
            assertEquals("Sisipan bilah status harus ditambahkan ke padding XML",
                    padXml + extra, header.getPaddingTop());

            // Sisipan dilaporkan ulang (mis. papan tombol dibuka-tutup):
            // padding tidak boleh bertambah lagi.
            header.dispatchApplyWindowInsets(statusBarInset(extra));
            assertEquals("Sisipan berulang tidak boleh menumpuk",
                    padXml + extra, header.getPaddingTop());
        }

        // Header harus tetap satu baris ringkas: kedua keterangan dibatasi satu
        // baris sehingga tingginya tidak dapat bertambah walau teksnya panjang.
        android.widget.TextView name = a.findViewById(R.id.hdr_name);
        android.widget.TextView sub = a.findViewById(R.id.hdr_sub);
        assertEquals("Nama pengguna harus dipotong satu baris", 1, name.getMaxLines());
        assertEquals("Keterangan harus dipotong satu baris", 1, sub.getMaxLines());
        assertEquals(android.text.TextUtils.TruncateAt.END, name.getEllipsize());
        c.pause().stop().destroy();
    }

    /** Sisipan bilah status buatan pada API tempat tipe ini tersedia. */
    private android.view.WindowInsets statusBarInset(int topPx) {
        return new android.view.WindowInsets.Builder()
                .setSystemWindowInsets(android.graphics.Insets.of(0, topPx, 0, 0))
                .build();
    }

    /** Membuat pesanan satu produk lalu menandainya lunas. */
    private Models.Order beliDanBayar(Models.User pembeli, int qty) throws Exception {
        Repository repo = Repository.get(ctx);
        repo.cartAdd("PRD-HBA-001", qty);
        Models.User terbaru = repo.user(pembeli.userId);
        Models.Order o = repo.createOrder(terbaru, terbaru.name, terbaru.contact(),
                "Jl. Melati No. 12", "Karawang", "41361", "");
        repo.submitPayment(o.orderId, terbaru.name, o.total, "BCA", "");
        repo.markStatus(repo.order(o.orderId), "PAID", "USR-ADMIN", "lunas");
        return o;
    }

    /**
     * Banner AdMob harus benar-benar terpasang di layar Member dengan unit iklan
     * yang dipilih runtime. Bila banner ditulis di XML, SDK menolak memuat dan
     * unit uji tidak pernah dipakai; pengujian ini mengunci perilaku tersebut.
     */
    @Test public void bannerIklanTerpasangDenganUnitYangBenar() throws Exception {
        Models.User u = Repository.get(ctx).register("Siti Aminah", "081234567890", "rahasia1", null);
        Session.set(ctx, u);

        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        android.view.ViewGroup wadah = a.findViewById(R.id.member_banner);
        assertNotNull("Wadah banner tidak ada", wadah);
        assertEquals("Wadah banner harus memuat tepat satu AdView", 1, wadah.getChildCount());
        com.google.android.gms.ads.AdView banner =
                (com.google.android.gms.ads.AdView) wadah.getChildAt(0);
        assertEquals("Unit iklan banner harus berasal dari Config",
                com.altomedia.herbalindo.core.Config.bannerUnit(), banner.getAdUnitId());
        c.pause().stop().destroy();
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