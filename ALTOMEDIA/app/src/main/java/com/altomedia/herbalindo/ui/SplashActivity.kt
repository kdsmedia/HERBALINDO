package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.User
import com.altomedia.herbalindo.ui.admin.AdminActivity
import com.altomedia.herbalindo.util.SessionStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves the current session and routes to the member or admin surface. */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repository.init(this)

        lifecycleScope.launch {
            val user = resolveUser()
            val next = when {
                user == null -> AuthActivity::class.java
                user.isAdmin -> AdminActivity::class.java
                else -> MainActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }
    }

    /**
     * Prefers a cached profile for an instant start; only hits Firestore when the
     * session is new on this device. A short timeout keeps a flaky network from
     * leaving the user on the splash screen.
     */
    private suspend fun resolveUser(): User? {
        val session = SessionStore(this)
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser ?: run {
            session.clear()
            return null
        }

        val cachedRole = session.role
        val cachedUid = session.uid

        return withContext(Dispatchers.IO) {
            val remote = withTimeoutOrNull(8_000L) {
                runCatching { Repository.getUser(current.uid) }.getOrNull()
            }
            when {
                remote != null -> {
                    if (!remote.isActive) {
                        auth.signOut()
                        session.clear()
                        null
                    } else {
                        session.uid = remote.uid
                        session.name = remote.name
                        session.referralId = remote.referralId
                        session.role = remote.role
                        remote
                    }
                }
                // Offline but previously signed in: trust the cached role to stay usable.
                cachedUid == current.uid && cachedRole.isNotEmpty() -> User(
                    uid = current.uid,
                    name = session.name,
                    referralId = session.referralId,
                    role = cachedRole,
                )
                else -> null
            }
        }
    }
}