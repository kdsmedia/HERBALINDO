package com.altomedia.herbalindo.ui.member;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.ads.AdsManager;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.AuthActivity;
import com.altomedia.herbalindo.ui.BaseActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Rumah layar Member. Menampilkan 5 tab (Home, Produk, Tugas, Saldo, Profil)
 * dan memasang banner AdMob di bagian bawah area konten.
 */
public class MemberActivity extends BaseActivity {

    private ViewGroup container;
    private TextView headerName, headerSub, headerCart, headerLevel;
    private BottomNavigationView nav;
    private String activeTab = "home";

    private HomeTab homeTab;
    private ProductsTab productsTab;
    private TasksTab tasksTab;
    private BalanceTab balanceTab;
    private ProfileTab profileTab;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        // Sesi dibaca lebih awal karena show("home") di bawah langsung memakai
        // data pengguna untuk tab pertama. Bila null, onResume akan mengalihkan
        // ke layar masuk.
        user = Session.current(this);
        if (user == null) { toAuth(); return; }
        setContentView(R.layout.activity_member);
        applyInsets();

        container = findViewById(R.id.tab_container);
        headerName = findViewById(R.id.hdr_name);
        headerSub = findViewById(R.id.hdr_sub);
        headerCart = findViewById(R.id.hdr_cart);
        headerLevel = findViewById(R.id.hdr_level);
        nav = findViewById(R.id.bottom_nav);

        homeTab = new HomeTab(this);
        productsTab = new ProductsTab(this);
        tasksTab = new TasksTab(this);
        balanceTab = new BalanceTab(this);
        profileTab = new ProfileTab(this);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) show("home");
            else if (id == R.id.nav_products) show("products");
            else if (id == R.id.nav_tasks) show("tasks");
            else if (id == R.id.nav_balance) show("balance");
            else if (id == R.id.nav_profile) show("profile");
            return true;
        });
        headerCart.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));

        AdsManager.get(this).loadBanner(findViewById(R.id.member_banner));
        // Disiapkan lebih awal agar interstitial sudah siap saat checkout selesai.
        AdsManager.get(this).preloadInterstitial();

        show("home");
    }

    @Override protected void onSessionReady(Models.User u) {
        if (headerName == null) return;
        // XP keaktifan harian diberikan sekali saat layar member dibuka.
        repo.grantDailyActive(u.userId);
        u = repo.user(u.userId);
        user = u;
        headerName.setText(u.name);
        headerSub.setText("REF " + u.referralId + " · " + Util.rupiah(repo.pointsToRupiah(u.points)));
        headerCart.setText("Keranjang " + repo.cart().size());
        refreshLevelBadge(u);
        refreshActiveTab();
    }

    /** Badge level di header ikut berubah setiap kali XP bertambah. */
    private void refreshLevelBadge(Models.User u) {
        if (headerLevel == null) return;
        int level = com.altomedia.herbalindo.level.Levels.levelFor(u.xp);
        headerLevel.setText("Lv" + level);
    }

    @Override protected void onResume() {
        super.onResume();
        if (headerCart != null) headerCart.setText("Keranjang " + repo.cart().size());
    }

    @Override protected void onDestroy() {
        AdsManager.get(this).destroyBanner(findViewById(R.id.member_banner));
        super.onDestroy();
    }

    void refreshHeader() {
        if (headerCart != null) headerCart.setText("Keranjang " + repo.cart().size());
    }

    /**
     * Hanya header yang menerima tambahan jarak atas; akar tata letak tidak
     * boleh ikut menerima sisipan karena layar ini memakai {@code adjustResize}
     * sehingga isi yang bisa digulir akan kehilangan ruang saat papan tombol
     * muncul. Sisa jendela tetap lewat kurungan sistem ({@code decorFitsSystemWindows}).
     */
    @Override protected View insetTarget() {
        return findViewById(R.id.member_header);
    }

    @Override protected boolean insetTopOnly() { return true; }

    void show(String tab) {
        if (isFinishing()) return;
        activeTab = tab;
        View view;
        switch (tab) {
            case "products": view = productsTab.view(); break;
            case "tasks": view = tasksTab.view(); break;
            case "balance": view = balanceTab.view(); break;
            case "profile": view = profileTab.view(); break;
            default: view = homeTab.view(); break;
        }
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
        container.removeAllViews();
        container.addView(view);
        refreshActiveTab();
        refreshHeader();
    }

    private void refreshActiveTab() {
        if (activeTab == null || container.getChildCount() == 0) return;
        switch (activeTab) {
            case "products": productsTab.refresh(); break;
            case "tasks": tasksTab.refresh(); break;
            case "balance": balanceTab.refresh(); break;
            case "profile": profileTab.refresh(); break;
            default: homeTab.refresh(); break;
        }
    }

    void openProduct(String productId) {
        Intent i = new Intent(this, ProductDetailActivity.class);
        i.putExtra("productId", productId);
        startActivity(i);
    }

    void goToTab(String tab) {
        int id;
        switch (tab) {
            case "products": id = R.id.nav_products; break;
            case "tasks": id = R.id.nav_tasks; break;
            case "balance": id = R.id.nav_balance; break;
            case "profile": id = R.id.nav_profile; break;
            default: id = R.id.nav_home; break;
        }
        nav.setSelectedItemId(id);
    }

    void logout() {
        Ui.confirm(this, "Keluar", "Keluar dari akun ini?", () -> {
            Session.clear(this);
            toAuth();
        });
    }
}
