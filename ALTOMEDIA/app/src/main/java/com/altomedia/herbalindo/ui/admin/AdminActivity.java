package com.altomedia.herbalindo.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.ui.AuthActivity;
import com.altomedia.herbalindo.ui.BaseActivity;
import com.google.android.material.tabs.TabLayout;

/** Panel Admin: ringkasan, pesanan, produk, member, withdrawal, pengaturan, audit. */
public class AdminActivity extends BaseActivity {

    private ViewGroup container;
    private TabLayout tabs;
    private String active = "dash";

    private DashboardSection dash;
    private OrdersSection orders;
    private ProductsSection products;
    private MembersSection members;
    private WithdrawalsSection withdrawals;
    private SettingsSection settings;
    private LogsSection logs;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        Models.User u = Session.current(this);
        if (u == null) { toAuth(); return; }
        if (!"ADMIN".equals(u.role)) { Ui.error(this, "Akses admin diperlukan"); finish(); return; }

        setContentView(R.layout.activity_admin);
        container = findViewById(R.id.adm_container);
        tabs = findViewById(R.id.adm_tabs);
        findViewById(R.id.adm_logout).setOnClickListener(v -> Ui.confirm(this, "Keluar",
                "Keluar dari panel admin?", () -> { Session.clear(this); toAuth(); }));

        dash = new DashboardSection(this);
        orders = new OrdersSection(this);
        products = new ProductsSection(this);
        members = new MembersSection(this);
        withdrawals = new WithdrawalsSection(this);
        settings = new SettingsSection(this);
        logs = new LogsSection(this);

        String[][] defs = {
                {"dash", "Ringkasan"}, {"orders", "Pesanan"}, {"products", "Produk & Stok"},
                {"members", "Member"}, {"withdrawals", "Withdrawal"}, {"settings", "Pengaturan"},
                {"logs", "Audit Log"}};
        for (String[] d : defs) tabs.addTab(tabs.newTab().setText(d[1]).setTag(d[0]));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { show((String) tab.getTag()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { show((String) tab.getTag()); }
        });
        show("dash");
    }

    @Override protected void onSessionReady(Models.User user) {
        Models.User u = Session.current(this);
        if (u == null || !"ADMIN".equals(u.role)) { toAuth(); return; }
        TextView sub = findViewById(R.id.adm_sub);
        if (sub != null) sub.setText(u.name + " · " + u.contact());
        refreshActive();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null) refreshActive();
    }

    void show(String section) {
        if (isFinishing()) return;
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

    /** Menampilkan satu baris informasi ringkas. */
    static View row(AdminActivity a, String label, String value, int colorRes) {
        View v = LayoutInflater.from(a).inflate(R.layout.item_info, null, false);
        ((TextView) v.findViewById(R.id.info_label)).setText(label);
        TextView tv = v.findViewById(R.id.info_value);
        tv.setText(value);
        tv.setTextColor(a.getColor(colorRes));
        return v;
    }
}