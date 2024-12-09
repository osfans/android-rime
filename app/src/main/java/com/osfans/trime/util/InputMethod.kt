/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import splitties.systemservices.inputMethodManager

@Suppress("DEPRECATION")
fun InputMethodService.forceShowSelf() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        requestShowSelf(InputMethodManager.SHOW_FORCED)
    } else {
        inputMethodManager.showSoftInputFromInputMethod(
            window.window!!.attributes.token,
            InputMethodManager.SHOW_FORCED,
        )
    }
}

fun InputConnection.monitorCursorAnchor(enable: Boolean = true): Boolean {
    if (!enable) {
        requestCursorUpdates(0)
        return false
    }
    var scheduled = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        scheduled =
            requestCursorUpdates(
                InputConnection.CURSOR_UPDATE_MONITOR,
                InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS or InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER,
            )
    }
    if (!scheduled) {
        scheduled = requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
    }
    return scheduled
}
