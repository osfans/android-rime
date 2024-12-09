/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import android.widget.PopupWindow
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject

@InputScope
@Inject
class PreeditModule(
    context: Context,
    theme: Theme,
    rime: RimeSession,
    private val bar: QuickBar,
) : InputBroadcastReceiver {
    val ui =
        PreeditUi(context, theme).apply {
            preedit.setOnCursorMoveListener { position ->
                rime.launchOnReady { it.moveCursorPos(position) }
            }
        }

    private val window =
        PopupWindow(ui.root).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        ui.update(ctx.composition)
        if (ctx.composition.length > 0) {
            val (x, y) = intArrayOf(0, 0).also { bar.view.getLocationInWindow(it) }
            window.showAtLocation(bar.view, Gravity.START or Gravity.TOP, x, y)
            ui.root.post {
                window.update(x, y - ui.root.height, -1, -1)
            }
        } else {
            window.dismiss()
        }
    }
}
