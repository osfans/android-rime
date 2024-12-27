/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

open class BaseMessageHandler(
    val service: TrimeInputMethodService,
    val rime: RimeSession,
) {
    open fun handleRimeMessage(it: RimeMessage<*>) {}

    private var messageHandlerJob: Job? = null

    private fun setupFcitxEventHandler() {
        messageHandlerJob =
            service.lifecycleScope.launch {
                rime.run { messageFlow }.collect {
                    handleRimeMessage(it)
                }
            }
    }

    var handleMessage = false
        set(value) {
            field = value
            if (field) {
                if (messageHandlerJob == null) {
                    setupFcitxEventHandler()
                }
            } else {
                messageHandlerJob?.cancel()
                messageHandlerJob = null
            }
        }

    fun cancelJob() {
        handleMessage = false
    }
}
