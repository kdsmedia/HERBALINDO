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
import com.altomedia.herbalindo.ui.member.MemberActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class AdminDataReproTest {
    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
    }

    @Test public void semuaTabAdminDenganData() throws Exception {
        Repository r = Repository.get(ctx);
        Models.User member = r.register("Siti Aminah", "081234567890", "rahasia1", null);
        Models.User friend = r.register("Budi Santoso", "081234567891", "rahasia1", member.referralId);
        r.addPoints(member.userId, 900000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) r.watchAd(member.userId);
        r.requestWithdrawal(r.user(member.userId), 50000, "BCA", "Siti Aminah", "1234567890");
        r.cartAdd("PRD-HBA-001", 2);
        Models.Order o = r.createOrder(r.user(member.userId), "Siti Aminah", "081234567890",
                "Jl. Melati 1", "Jakarta", "40111", "lunas");
        r.submitPayment(o.orderId, "Siti Aminah", o.total, "BCA", "lunas");
        r.markStatus(r.order(o.orderId), "PAID", "USR-ADMIN", "verifikasi");
        r.cartAdd("PRD-HBA-002", 1);
        Models.Order o2 = r.createOrder(r.user(friend.userId), "Budi Santoso", "081234567891",
                "Jl. Mawar 2", "Bandung", "40112", "kirim");
        r.submitPayment(o2.orderId, "Budi Santoso", o2.total, "OVO", "lunas");
        r.adminSetPoints(member.userId, 12000, "koreksi", "USR-ADMIN");

        Models.User admin = r.user("USR-ADMIN");
        Session.set(ctx, admin);
        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        int[] menus = {R.id.adm_nav_dash, R.id.adm_nav_orders, R.id.adm_nav_products,
                R.id.adm_nav_members, R.id.adm_nav_withdrawals, R.id.adm_nav_settings,
                R.id.adm_nav_logs};
        for (int id : menus) {
            System.out.println(">>> MENU " + id);
            a.findViewById(id).performClick();
            System.out.println(">>> MENU " + id + " OK");
        }
        c.pause().stop().destroy();
    }

    /** Admin yang membuka layar member harus tetap berjalan tanpa menutup aplikasi. */
    @Test public void adminMembukaLayarMemberTidakMenutup() throws Exception {
        Repository r = Repository.get(ctx);
        Models.User admin = r.user("USR-ADMIN");
        Session.set(ctx, admin);
        ActivityController<MemberActivity> c = Robolectric.buildActivity(MemberActivity.class).setup();
        MemberActivity a = c.get();
        System.out.println(">>> member finishing=" + a.isFinishing());
        System.out.println(">>> next=" + org.robolectric.Shadows.shadowOf(a).getNextStartedActivity());
        for (int id : new int[]{R.id.nav_home, R.id.nav_products, R.id.nav_tasks,
                R.id.nav_balance, R.id.nav_profile}) {
            ((com.google.android.material.bottomnavigation.BottomNavigationView)
                    a.findViewById(R.id.bottom_nav)).setSelectedItemId(id);
            System.out.println(">>> tab " + id + " OK");
        }
        c.pause().stop().destroy();
    }
}
