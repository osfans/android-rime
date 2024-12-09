/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeCallback
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

open class BaseCallbackHandler(
    val service: TrimeInputMethodService,
    val rime: RimeSession,
) {
    open fun handleRimeCallback(it: RimeCallback) {}

    private var callbackHandlerJob: Job? = null

    private fun setupFcitxEventHandler() {
        callbackHandlerJob =
            service.lifecycleScope.launch {
                rime.run { callbackFlow }.collect {
                    handleRimeCallback(it)
                }
            }
    }

    var handleCallback = false
        set(value) {
            field = value
            if (field) {
                if (callbackHandlerJob == null) {
                    setupFcitxEventHandler()
                }
            } else {
                callbackHandlerJob?.cancel()
                callbackHandlerJob = null
            }
        }

    fun cancelJob() {
        handleCallback = false
    }
}
