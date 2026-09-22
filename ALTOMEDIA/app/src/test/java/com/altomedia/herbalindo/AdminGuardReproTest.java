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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class AdminGuardReproTest {
    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
    }

    /** Member yang membuka panel admin harus ditolak dengan rapi, bukan crash. */
    @Test public void memberMembukaPanelAdminTidakCrash() throws Exception {
        Models.User member = Repository.get(ctx).register("Siti Aminah", "081234567890", "rahasia1", null);
        Session.set(ctx, member);
        ActivityController<AdminActivity> c;
        try {
            c = Robolectric.buildActivity(AdminActivity.class).setup();
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Membuka panel admin sebagai member membuat aplikasi berhenti: " + t);
            return;
        }
        AdminActivity a = c.get();
        System.out.println(">>> finishing=" + a.isFinishing());
        try { c.pause().stop().destroy(); } catch (Throwable t) {
            t.printStackTrace();
            fail("Penutupan layar panel admin gagal: " + t);
        }
    }

    /** Tanpa sesi sama sekali pun panel admin tidak boleh menjatuhkan aplikasi. */
    @Test public void tanpaSesiMembukaPanelAdminTidakCrash() {
        ActivityController<AdminActivity> c;
        try {
            c = Robolectric.buildActivity(AdminActivity.class).setup();
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Membuka panel admin tanpa sesi membuat aplikasi berhenti: " + t);
            return;
        }
        c.pause().stop().destroy();
    }
}
