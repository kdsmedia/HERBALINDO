package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.databinding.ActivityAuthBinding
import com.altomedia.herbalindo.ui.admin.AdminActivity
import com.altomedia.herbalindo.ui.widgets.showSnack
import com.altomedia.herbalindo.util.SessionStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Single entry point for both members and admins (BAB 2.3). The server decides the role
 * after sign-in; there is deliberately no separate admin login.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var session: SessionStore
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance(com.altomedia.herbalindo.Config.FUNCTIONS_REGION) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionStore(this)

        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val isLogin = binding.tabs.selectedTabPosition == 0
                binding.loginGroup.visibility = if (isLogin) View.VISIBLE else View.GONE
                binding.registerGroup.visibility = if (isLogin) View.GONE else View.VISIBLE
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
        })

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.btnForgot.setOnClickListener { sendPasswordReset() }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !busy
        binding.btnRegister.isEnabled = !busy
    }

    private fun attemptLogin() {
        val identity = binding.loginIdentity.text?.toString()?.trim().orEmpty()
        val password = binding.loginPassword.text?.toString().orEmpty()

        if (identity.isEmpty()) {
            binding.root.showSnack("Email / Nomor HP wajib diisi")
            return
        }
        if (password.length < 6) {
            binding.root.showSnack("Password minimal 6 karakter")
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            // Members may sign in with a phone number, which Firebase Auth does not accept
            // directly; resolve it to the stored email first.
            val email = if (identity.contains("@")) identity
            else resolveEmailFromPhone(identity)

            if (email == null) {
                setBusy(false)
                binding.root.showSnack("Nomor HP belum terdaftar")
                return@launch
            }

            val result = runCatching { auth.signInWithEmailAndPassword(email, password).await() }
            setBusy(false)
            result.onSuccess {
                routeAfterLogin()
            }.onFailure { e ->
                binding.root.showSnack(friendlyAuthError(e))
            }
        }
    }

    private suspend fun resolveEmailFromPhone(phone: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Repository.db.collection(com.altomedia.herbalindo.data.Col.USERS)
                .whereEqualTo("phone", phone)
                .limit(1)
                .get().await()
                .documents.firstOrNull()
                ?.getString("email")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun attemptRegister() {
        val name = binding.regName.text?.toString()?.trim().orEmpty()
        val email = binding.regEmail.text?.toString()?.trim().orEmpty()
        val phone = binding.regPhone.text?.toString()?.trim().orEmpty()
        val password = binding.regPassword.text?.toString().orEmpty()
        val referral = binding.regReferral.text?.toString()?.trim().orEmpty()

        when {
            name.length < 2 -> { binding.root.showSnack("Nama minimal 2 karakter"); return }
            email.isEmpty() -> { binding.root.showSnack("Email wajib diisi"); return }
            phone.isEmpty() -> { binding.root.showSnack("Nomor HP wajib diisi"); return }
            password.length < 6 -> { binding.root.showSnack("Password minimal 6 karakter"); return }
            referral.isNotEmpty() && !Regex("^[0-9]{6}$").matches(referral) -> {
                binding.root.showSnack("Referral ID harus tepat 6 digit angka"); return
            }
        }

        setBusy(true)
        lifecycleScope.launch {
            // Create the auth account, then let the backend mint the referral id and profile.
            val authResult = runCatching { auth.createUserWithEmailAndPassword(email, password).await() }
            if (authResult.isFailure) {
                setBusy(false)
                binding.root.showSnack(friendlyAuthError(authResult.exceptionOrNull()))
                return@launch
            }

            val profile = runCatching {
                functions.getHttpsCallable("registerProfile")
                    .call(mapOf(
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "referralId" to referral,
                    ))
                    .await()
            }

            if (profile.isFailure) {
                // Roll back the orphaned auth account so the member can retry cleanly.
                auth.currentUser?.delete()
                auth.signOut()
                setBusy(false)
                binding.root.showSnack(friendlyFunctionsError(profile.exceptionOrNull()))
                return@launch
            }

            val data = profile.getOrNull()?.data as? Map<*, *>
            session.uid = auth.currentUser?.uid.orEmpty()
            session.name = name
            session.referralId = data?.get("referralId")?.toString().orEmpty()
            session.role = "MEMBER"

            // Signing out here keeps the flow explicit: the member logs in to enter.
            auth.signOut()
            setBusy(false)

            binding.tabs.getTabAt(0)?.select()
            binding.loginIdentity.setText(email)
            binding.loginPassword.setText("")
            binding.root.showSnack(
                getString(R.string.register_success) + " Referral ID: ${session.referralId}",
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
        }
    }

    private fun sendPasswordReset() {
        val identity = binding.loginIdentity.text?.toString()?.trim().orEmpty()
        if (!identity.contains("@")) {
            binding.root.showSnack("Masukkan email Anda dulu untuk reset password")
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching { auth.sendPasswordResetEmail(identity).await() }
            setBusy(false)
            result.onSuccess {
                binding.root.showSnack("Email reset password telah dikirim")
            }.onFailure {
                binding.root.showSnack(friendlyAuthError(it))
            }
        }
    }

    private suspend fun routeAfterLogin() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            binding.root.showSnack("Sesi tidak valid, coba lagi")
            return
        }

        val user = withContext(Dispatchers.IO) {
            runCatching { Repository.getUser(uid) }.getOrNull()
        }

        if (user == null) {
            auth.signOut()
            binding.root.showSnack("Data member tidak ditemukan. Hubungi admin.")
            return
        }
        if (!user.isActive) {
            auth.signOut()
            binding.root.showSnack("Akun Anda tidak aktif. Hubungi admin.")
            return
        }

        session.uid = user.uid
        session.name = user.name
        session.referralId = user.referralId
        session.role = user.role

        val next = if (user.isAdmin) AdminActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }

    private fun friendlyAuthError(t: Throwable?): String = when (t) {
        is FirebaseAuthWeakPasswordException -> "Password terlalu lemah (minimal 6 karakter)"
        is FirebaseAuthUserCollisionException -> "Email sudah terdaftar"
        is FirebaseAuthInvalidCredentialsException -> "Email atau password salah"
        else -> t?.message ?: "Gagal memproses permintaan"
    }

    private fun friendlyFunctionsError(t: Throwable?): String {
        val message = (t as? com.google.firebase.functions.FirebaseFunctionsException)?.message
        return message ?: t?.message ?: "Pendaftaran gagal"
    }
}