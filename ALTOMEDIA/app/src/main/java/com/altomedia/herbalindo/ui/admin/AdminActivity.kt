package com.altomedia.herbalindo.ui.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.databinding.ActivityAdminBinding
import com.altomedia.herbalindo.util.SessionStore

/**
 * Admin console (BAB 10 / 11). Reaching this activity requires role ADMIN, and every
 * mutating action it triggers is authorised again server-side by requireAdmin().
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Repository.init(this)

        val session = SessionStore(this)
        if (!session.isAdmin) {
            finish()
            return
        }

        binding.toolbar.tvTitle.text = getString(R.string.admin_dashboard)

        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                show(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
        })

        if (savedInstanceState == null) show(0)
    }

    private fun show(position: Int) {
        val fragment: Fragment = when (position) {
            0 -> AdminDashboardFragment()
            1 -> AdminMembersFragment()
            2 -> AdminProductsFragment()
            3 -> AdminOrdersFragment()
            4 -> AdminWithdrawalsFragment()
            else -> AdminLogsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commit()
    }

    fun openSettings() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, AdminSettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    fun openProductEditor(productId: String?) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, AdminProductEditFragment.newInstance(productId))
            .addToBackStack(null)
            .commit()
    }

    fun openMemberDetail(uid: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, AdminMemberDetailFragment.newInstance(uid))
            .addToBackStack(null)
            .commit()
    }
}