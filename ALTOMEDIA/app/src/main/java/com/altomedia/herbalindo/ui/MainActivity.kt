package com.altomedia.herbalindo.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.altomedia.herbalindo.R
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.databinding.ActivityMainBinding
import com.altomedia.herbalindo.ui.widgets.BannerAds

/** Member shell: five tabs over a shared AdMob banner (BAB 14.2 / 14.3). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Repository.init(this)

        BannerAds.attach(this, binding.bannerContainer)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_produk -> ProductsFragment()
                R.id.nav_tugas -> TasksFragment()
                R.id.nav_saldo -> BalanceFragment()
                R.id.nav_profil -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    /** Lets Home jump straight to a tab, e.g. the wallet card opening Saldo. */
    fun navigateTo(menuId: Int) {
        binding.bottomNav.selectedItemId = menuId
    }

    fun openActivity(clazz: Class<*>) {
        startActivity(Intent(this, clazz))
    }
}