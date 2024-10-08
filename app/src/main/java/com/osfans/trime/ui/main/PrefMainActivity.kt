// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.R
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.sound.SoundEffectManager
import com.osfans.trime.databinding.ActivityPrefBinding
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.util.isStorageAvailable
import com.osfans.trime.util.progressBarDialogIndeterminate
import com.osfans.trime.util.rimeActionWithResultDialog
import kotlinx.coroutines.launch
import splitties.views.topPadding

class PrefMainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val prefs = AppPrefs.defaultInstance()

    private lateinit var navHostFragment: NavHostFragment
    private var loadingDialog: AlertDialog? = null

    private fun onNavigateUpListener(): Boolean {
        val navController = navHostFragment.navController
        return when (navController.currentDestination?.id) {
            R.id.prefFragment -> {
                // "minimize" the app, don't exit activity
                moveTaskToBack(false)
                true
            }
            else -> onSupportNavigateUp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val uiMode =
            when (prefs.other.uiMode) {
                AppPrefs.Other.UiMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppPrefs.Other.UiMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppPrefs.Other.UiMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        AppCompatDelegate.setDefaultNightMode(uiMode)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityPrefBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
            }
            binding.prefToolbar.root.topPadding = systemBars.top
            windowInsets
        }
        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setContentView(binding.root)
        setSupportActionBar(binding.prefToolbar.toolbar)
        val appBarConfiguration =
            AppBarConfiguration(
                topLevelDestinationIds = setOf(),
                fallbackOnNavigateUpListener = ::onNavigateUpListener,
            )
        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.prefToolbar.toolbar.setupWithNavController(navHostFragment.navController, appBarConfiguration)
        viewModel.toolbarTitle.observe(this) {
            binding.prefToolbar.toolbar.title = it
        }
        viewModel.topOptionsMenu.observe(this) {
            binding.prefToolbar.toolbar.menu.forEach { m ->
                m.isVisible = it
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (SetupActivity.shouldSetup()) {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        checkNotificationPermission()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rime.run { stateFlow }.collect { state ->
                    when (state) {
                        RimeLifecycle.State.STARTING -> {
                            loadingDialog?.dismiss()
                            loadingDialog =
                                progressBarDialogIndeterminate(R.string.deploy_progress).create().apply {
                                    show()
                                }
                        }
                        RimeLifecycle.State.READY -> loadingDialog?.dismiss()
                        else -> return@collect
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.preference_main_menu, menu)
        menu?.forEach {
            it.isVisible = viewModel.topOptionsMenu.value ?: true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.preference__menu_deploy -> {
                deploy()
                true
            }
            R.id.preference__menu_about -> {
                navHostFragment.navController.navigate(R.id.action_prefFragment_to_aboutFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun deploy() {
        lifecycleScope.launch {
            rimeActionWithResultDialog("rime.trime", "W", 1) {
                RimeDaemon.restartRime(true)
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isStorageAvailable()) {
            SoundEffectManager.init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun checkNotificationPermission() {
        if (XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)) {
            return
        } else {
            AlertDialog
                .Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.grant_permission) { _, _ ->
                    XXPermissions
                        .with(this)
                        .permission(Permission.POST_NOTIFICATIONS)
                        .request(null)
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
