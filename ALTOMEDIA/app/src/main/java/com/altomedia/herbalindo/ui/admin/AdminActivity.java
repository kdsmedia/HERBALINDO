package com.altomedia.herbalindo.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.ui.BaseActivity;

/**
 * Panel Admin: ringkasan, pesanan, produk, member, withdrawal, pengaturan, audit.
 *
 * <p>Navigasi antar bagian memakai menu samping yang meluncur dari kiri
 * ({@link DrawerLayout}). Sebelumnya tujuh bagian ditampilkan sebagai deretan
 * tab sehingga label panjang terpotong dan satu tangan sulit menjangkaunya.</p>
 *
 * <p>Sesi diperiksa dua kali. Pemeriksaan di {@code onCreate} hanya berjalan
 * ketika pengguna membuka layar ini dari dalam aplikasi; sesudah itu
 * {@code BaseActivity.onResume} memeriksa ulang setiap kali layar kembali
 * aktif. Layar yang ditutup di tengah {@code onCreate} tetap menjalani
 * {@code onResume} sebelum benar-benar selesai, sehingga {@code container}
 * belum tentu ada dan setiap pemakaiannya wajib diperiksa lebih dulu.</p>
 */
public class AdminActivity extends BaseActivity {

    private ViewGroup container;
    private DrawerLayout drawer;
    private TextView sectionLabel;

    private String active = "dash";

    private DashboardSection dash;
    private OrdersSection orders;
    private ProductsSection products;
    private MembersSection members;
    private WithdrawalsSection withdrawals;
    private SettingsSection settings;
    private LogsSection logs;

    /** Bagian menu: kunci bagian dan label yang ditampilkan di header. */
    private static final String[][] SECTIONS = {
            {"dash", "Ringkasan"},
            {"orders", "Pesanan"},
            {"products", "Produk & Stok"},
            {"members", "Member"},
            {"withdrawals", "Withdrawal"},
            {"settings", "Pengaturan"},
            {"logs", "Audit Log"}};

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        Models.User u = Session.current(this);
        if (u == null) { toAuth(); return; }
        if (!"ADMIN".equals(u.role)) { Ui.error(this, "Akses admin diperlukan"); finish(); return; }

        setContentView(R.layout.activity_admin);
        applyInsets();
        container = findViewById(R.id.adm_container);
        drawer = findViewById(R.id.adm_drawer);
        sectionLabel = findViewById(R.id.adm_title);

        findViewById(R.id.adm_menu).setOnClickListener(v -> openDrawer());
        View.OnClickListener keluar = v -> confirmLogout();
        findViewById(R.id.adm_logout).setOnClickListener(keluar);
        findViewById(R.id.adm_nav_logout).setOnClickListener(keluar);

        dash = new DashboardSection(this);
        orders = new OrdersSection(this);
        products = new ProductsSection(this);
        members = new MembersSection(this);
        withdrawals = new WithdrawalsSection(this);
        settings = new SettingsSection(this);
        logs = new LogsSection(this);

        nav(R.id.adm_nav_dash, "dash");
        nav(R.id.adm_nav_orders, "orders");
        nav(R.id.adm_nav_products, "products");
        nav(R.id.adm_nav_members, "members");
        nav(R.id.adm_nav_withdrawals, "withdrawals");
        nav(R.id.adm_nav_settings, "settings");
        nav(R.id.adm_nav_logs, "logs");

        show("dash");
    }

    /** Menyambungkan satu baris menu samping ke bagiannya. */
    private void nav(int viewId, String section) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> { show(section); closeDrawer(); });
    }

    private void openDrawer() {
        if (drawer != null) drawer.openDrawer(GravityCompat.START);
    }

    private void closeDrawer() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
    }

    private void confirmLogout() {
        closeDrawer();
        Ui.confirm(this, "Keluar", "Keluar dari panel admin?", () -> {
            Session.clear(this);
            toAuth();
        });
    }

    @Override protected void onSessionReady(Models.User user) {
        Models.User u = Session.current(this);
        if (u == null || !"ADMIN".equals(u.role)) { toAuth(); return; }
        TextView sub = findViewById(R.id.adm_sub);
        if (sub != null) sub.setText(u.name + " · " + u.contact());
        TextView identity = findViewById(R.id.adm_nav_identity);
        if (identity != null) identity.setText(u.name + "\n" + u.contact());
        refreshActive();
    }

    @Override protected void onResume() {
        super.onResume();
        if (container == null) return;
        if (repo != null) refreshActive();
    }

    /** Tombol kembali menutup menu samping lebih dulu, bukan langsung keluar. */
    @Override public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    void show(String section) {
        if (isFinishing() || container == null) return;
        active = section;
        View v;
        switch (section) {
            case "orders": v = orders.view(); break;
            case "products": v = products.view(); break;
            case "members": v = members.view(); break;
            case "withdrawals": v = withdrawals.view(); break;
            case "settings": v = settings.view(); break;
            case "logs": v = logs.view(); break;
            default: v = dash.view(); break;
        }
        ViewGroup parent = (ViewGroup) v.getParent();
        if (parent != null) parent.removeView(v);
        container.removeAllViews();
        container.addView(v);
        refreshActive();
    }

    void refreshActive() {
        if (container == null) return;
        // Judul header mengikuti bagian yang sedang dibuka agar pengguna tetap
        // tahu posisinya walau menu samping sudah tertutup.
        if (sectionLabel != null) sectionLabel.setText(labelFor(active));
        if (container.getChildCount() == 0) return;
        switch (active) {
            case "orders": orders.refresh(); break;
            case "products": products.refresh(); break;
            case "members": members.refresh(); break;
            case "withdrawals": withdrawals.refresh(); break;
            case "settings": settings.refresh(); break;
            case "logs": logs.refresh(); break;
            default: dash.refresh(); break;
        }
    }

    private static String labelFor(String section) {
        for (String[] d : SECTIONS) if (d[0].equals(section)) return d[1];
        return "Ringkasan";
    }

    /** Menampilkan satu baris informasi ringkas. */
    static View row(AdminActivity a, String label, String value, int colorRes) {
        View v = LayoutInflater.from(a).inflate(R.layout.item_info, null, false);
        ((TextView) v.findViewById(R.id.info_label)).setText(label);
        TextView tv = v.findViewById(R.id.info_value);
        tv.setText(value);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(a, colorRes));
        return v;
    }
}
