// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.lifecycleScope
import com.osfans.trime.core.whenReady
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.util.appContext
import com.osfans.trime.util.logcat
import com.osfans.trime.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import splitties.systemservices.notificationManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manage the singleton instance of [Rime]
 *
 * To use rime, client should call [createSession] to obtain a [RimeSession],
 * and call [destroySession] on client destroyed. Client should not leak the instance of [RimeApi],
 * and must use [RimeSession] to access rime functionalities.
 *
 * The instance of [Rime] always exists,but whether the dispatcher runs and callback works depend on clients, i.e.
 * if no clients are connected, [Rime.finalize] will be called.
 *
 * Functions are thread-safe in this class.
 *
 * Adapted from [fcitx5-android/FcitxDaemon.kt](https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/daemon/FcitxDaemon.kt)
 */
object RimeDaemon {
    private val realRime by lazy { Rime() }

    private val rimeImpl by lazy { object : RimeApi by realRime {} }

    private val sessions = mutableMapOf<String, RimeSession>()

    private val lock = ReentrantLock()

    private fun establish(name: String) =
        object : RimeSession {
            private inline fun <T> ensureEstablished(block: () -> T) =
                if (name in sessions) {
                    block()
                } else {
                    throw IllegalStateException("Session $name is not established")
                }

            override fun <T> run(block: suspend RimeApi.() -> T): T =
                ensureEstablished {
                    runBlocking { block(rimeImpl) }
                }

            override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T =
                ensureEstablished {
                    realRime.lifecycle.whenReady { block(rimeImpl) }
                }

            override fun runIfReady(block: suspend RimeApi.() -> Unit) {
                ensureEstablished {
                    if (realRime.isReady) {
                        realRime.lifecycleScope.launch {
                            block(rimeImpl)
                        }
                    }
                }
            }

            override val lifecycleScope: CoroutineScope
                get() = realRime.lifecycle.lifecycleScope
        }

    fun createSession(name: String): RimeSession =
        lock.withLock {
            if (name in sessions) {
                return@withLock sessions.getValue(name)
            }
            if (realRime.lifecycle.currentStateFlow.value == RimeLifecycle.State.STOPPED) {
                realRime.startup(false)
            }
            val session = establish(name)
            sessions[name] = session
            return@withLock session
        }

    fun destroySession(name: String): Unit =
        lock.withLock {
            if (name !in sessions) {
                return
            }
            sessions -= name
            if (sessions.isEmpty()) {
                realRime.finalize()
            }
        }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getText(R.string.rime_daemon),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = CHANNEL_ID }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private const val CHANNEL_ID = "rime-daemon"
    private const val MESSAGE_ID = 2331
    private var restartId = 0

    /**
     * Restart Rime instance to deploy while keep the session
     */
    fun restartRime(fullCheck: Boolean = false) =
        lock.withLock {
            val id = restartId++
            NotificationCompat
                .Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_sync_24)
                .setContentTitle(appContext.getString(R.string.rime_daemon))
                .setContentText(appContext.getString(R.string.restarting_rime))
                .setOngoing(true)
                .setProgress(100, 0, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
                .let { notificationManager.notify(id, it) }
            realRime.finalize()
            realRime.startup(fullCheck)
            TrimeApplication.getInstance().coroutineScope.launch {
                // cancel notification on ready
                realRime.lifecycle.whenReady {
                    notificationManager.cancel(id)
                }
            }
        }

    suspend fun onRimeMessage(
        ctx: Context,
        it: RimeMessage<*>,
    ) {
        if (it is RimeMessage.DeployMessage) {
            when (it.data) {
                RimeMessage.DeployMessage.State.Start -> {
                    withContext(Dispatchers.IO) {
                        logcat { clear() }
                    }
                }
                RimeMessage.DeployMessage.State.Success -> {
                    ContextCompat.getMainExecutor(ctx).execute {
                        ctx.toast(R.string.deploy_finish)
                    }
                }
                RimeMessage.DeployMessage.State.Failure -> {
                    val intent =
                        Intent(ctx, LogActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            val log =
                                withContext(Dispatchers.IO) {
                                    logcat {
                                        format("brief")
                                        filterspec("rime.trime", "W")
                                        dump()
                                    }.inputStream.bufferedReader().readText()
                                }
                            putExtra(LogActivity.FROM_DEPLOY, true)
                            putExtra(LogActivity.DEPLOY_FAILURE_TRACE, log)
                        }
                    NotificationCompat
                        .Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_baseline_warning_24)
                        .setContentTitle(ctx.getString(R.string.rime_daemon))
                        .setContentText(ctx.getString(R.string.view_deploy_failure_log))
                        .setContentIntent(
                            PendingIntent.getActivity(
                                ctx,
                                0,
                                intent,
                                PendingIntent.FLAG_ONE_SHOT or
                                    PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ).setOngoing(false)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                        .let { notificationManager.notify(MESSAGE_ID, it) }
                }
            }
        }
    }
}
