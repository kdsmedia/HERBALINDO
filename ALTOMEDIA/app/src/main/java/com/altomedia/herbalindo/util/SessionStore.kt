package com.altomedia.herbalindo.util

import android.content.Context
import androidx.core.content.edit

/**
 * Lightweight local cache of the signed-in member so screens can render instantly
 * on cold start instead of waiting for a network round trip.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("herbalindo_session", Context.MODE_PRIVATE)

    var uid: String
        get() = prefs.getString(KEY_UID, "") ?: ""
        set(v) = prefs.edit { putString(KEY_UID, v) }

    var name: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(v) = prefs.edit { putString(KEY_NAME, v) }

    var referralId: String
        get() = prefs.getString(KEY_REFERRAL, "") ?: ""
        set(v) = prefs.edit { putString(KEY_REFERRAL, v) }

    var role: String
        get() = prefs.getString(KEY_ROLE, "MEMBER") ?: "MEMBER"
        set(v) = prefs.edit { putString(KEY_ROLE, v) }

    var isAdmin: Boolean
        get() = role == "ADMIN"
        set(v) = prefs.edit { putString(KEY_ROLE, if (v) "ADMIN" else "MEMBER") }

    fun clear() = prefs.edit { clear() }

    companion object {
        private const val KEY_UID = "uid"
        private const val KEY_NAME = "name"
        private const val KEY_REFERRAL = "referral_id"
        private const val KEY_ROLE = "role"
    }
}