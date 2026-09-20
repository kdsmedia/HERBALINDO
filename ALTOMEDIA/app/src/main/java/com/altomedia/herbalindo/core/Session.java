package com.altomedia.herbalindo.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Sesi login yang disimpan lokal dan selalu diverifikasi ulang dari repositori. */
public final class Session {
    private Session() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);
    }

    public static void set(Context c, Models.User u) {
        prefs(c).edit().putString("userId", u.userId).putString("role", u.role).apply();
    }

    public static void clear(Context c) {
        prefs(c).edit().remove("userId").remove("role").remove("pendingRef").apply();
    }

    public static Models.User current(Context c) {
        String id = prefs(c).getString("userId", null);
        if (id == null) return null;
        Models.User u = Repository.get(c).user(id);
        if (u == null || !"ACTIVE".equals(u.status)) { clear(c); return null; }
        return u;
    }

    public static boolean isAdmin(Context c) {
        Models.User u = current(c);
        return u != null && "ADMIN".equals(u.role);
    }

    public static void setPendingReferral(Context c, String code) {
        if (code != null && code.matches("^\\d{6}$")) prefs(c).edit().putString("pendingRef", code).apply();
    }

    public static String pendingReferral(Context c) {
        return prefs(c).getString("pendingRef", null);
    }

    public static void clearPendingReferral(Context c) {
        prefs(c).edit().remove("pendingRef").apply();
    }
}