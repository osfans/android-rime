package com.osfans.trime.settings

import android.Manifest
import android.annotation.TargetApi
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.blankj.utilcode.util.BarUtils
import com.osfans.trime.R
import com.osfans.trime.databinding.PrefActivityBinding
import com.osfans.trime.ime.core.Preferences
import com.osfans.trime.settings.components.SchemaPickerDialog
import com.osfans.trime.util.ImeUtils
import com.osfans.trime.util.RimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

internal const val FRAGMENT_TAG = "FRAGMENT_TAG"

class PrefMainActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private val prefs get() = Preferences.defaultInstance()

    lateinit var binding: PrefActivityBinding
    lateinit var imeManager: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs.sync()
        val uiMode = when (prefs.other.uiMode) {
            "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
        }
        AppCompatDelegate.setDefaultNightMode(uiMode)

        super.onCreate(savedInstanceState)
        binding = PrefActivityBinding.inflate(layoutInflater)
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            BarUtils.setNavBarColor(
                this,
                getColor(R.color.windowBackground)
            )
        } else if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            BarUtils.setNavBarColor(
                this,
                @Suppress("DEPRECATION")
                resources.getColor(R.color.windowBackground)
            )
        }
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        imeManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        if (savedInstanceState == null) {
            loadFragment(PrefFragment())
        } else {
            title = savedInstanceState.getCharSequence(FRAGMENT_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.trime_app_name)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        requestPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(FRAGMENT_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.preference, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(binding.preference.id, fragment, FRAGMENT_TAG)
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.preference_main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.preference__menu_deploy -> {
                @Suppress("DEPRECATION")
                val progressDialog = ProgressDialog(this).apply {
                    setMessage(getString(R.string.deploy_progress))
                    show()
                }
                launch {
                    Runnable {
                        try {
                            RimeUtils.deploy(this@PrefMainActivity)
                        } catch (ex: Exception) {
                            Timber.e(ex, "Deploy Exception")
                        } finally {
                            progressDialog.dismiss()
//                              exitProcess(0)
                        }
                    }.run()
                }
                true
            }
            R.id.preference__menu_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.preference__menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @TargetApi(VERSION_CODES.M)
    private fun requestPermission() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                0
            )
        }
        if (VERSION.SDK_INT >= VERSION_CODES.P) { // 僅Android P需要此權限在最上層顯示懸浮窗、對話框
            if (!Settings.canDrawOverlays(this)) { // 事先说明需要权限的理由
                AlertDialog.Builder(this).apply {
                    setTitle(R.string.pref__draw_overlays_tip_title)
                    setCancelable(true)
                    setMessage(R.string.pref__draw_overlays_tip_message)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        // startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                        startActivity(intent)
                    }
                    setNegativeButton(android.R.string.cancel, null)
                }.create().show()
            }
        }
    }

    class PrefFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
            if (InputMethodUtils.checkIsTrimeEnabled(requireContext())) {
                findPreference<Preference>("pref_enable")?.isVisible = false
            }
            if (InputMethodUtils.checkisTrimeSelected(requireContext())) {
                findPreference<Preference>("pref_select")?.isVisible = false
            }
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "pref_enable" -> { // 啓用
                    InputMethodUtils.showImeEnablerActivity(requireContext())
                    true
                }
                "pref_select" -> { // 切換
                    InputMethodUtils.showImePicker(requireContext())
                }
                "pref_schemas" -> {
                    SchemaPickerDialog(requireContext()).show()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }

        override fun onResume() { // 如果同文已被启用/选用，则隐藏设置项
            super.onResume()
            if (InputMethodUtils.checkIsTrimeEnabled(requireContext())) {
                findPreference<Preference>("pref_enable")?.isVisible = false
            }
            if (InputMethodUtils.checkisTrimeSelected(requireContext())) {
                findPreference<Preference>("pref_select")?.isVisible = false
            }
        }
    }
}
