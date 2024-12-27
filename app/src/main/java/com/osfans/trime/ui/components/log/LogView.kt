// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.components.log

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.util.Logcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import splitties.resources.styledColor
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager

/**
 * A scroll view to look up the app log.
 *
 * This file is adapted from fcitx5-android project.
 * Source:
 * [fcitx5-android/LogView](https://github.com/fcitx5-android/fcitx5-android/blob/5ac719c3547165a3e77fe265c471a5a211580320/app/src/main/java/org/fcitx/fcitx5/android/ui/main/log/LogView.kt)
 */
class LogView
    @JvmOverloads
    constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
    ) : HorizontalScrollView(context, attributeSet) {
        private var logcat: Logcat? = null

        private val logAdapter = LogAdapter()

        private val recyclerView =
            recyclerView {
                adapter = logAdapter
                layoutManager = verticalLayoutManager()
            }

        init {
            add(
                recyclerView,
                lParams(wrapContent, matchParent),
            )
        }

        override fun onDetachedFromWindow() {
            logcat?.shutdownLogFlow()
            super.onDetachedFromWindow()
        }

        fun append(content: String) {
            logAdapter.append(
                buildSpannedString {
                    color(styledColor(android.R.attr.colorForeground)) { append(content) }
                },
            )
        }

        fun setLogcat(logcat: Logcat) {
            this.logcat = logcat
            logcat.initLogFlow()
            logcat.logFlow
                .onEach(::buildColoredString)
                .launchIn(findViewTreeLifecycleOwner()!!.lifecycleScope)
        }

        private fun buildColoredString(str: String) {
            val color =
                ContextCompat.getColor(
                    context,
                    when (str.codePointAt(19).toChar()) {
                        'V' -> R.color.grey_700
                        'D' -> R.color.grey_700
                        'I' -> R.color.blue_500
                        'W' -> R.color.yellow_800
                        'E' -> R.color.red_400
                        'F' -> R.color.red_A700
                        else -> R.color.colorPrimary
                    },
                )
            logAdapter.append(
                buildSpannedString {
                    color(color) { append(str) }
                },
            )
        }

        val currentLog: String
            get() = logAdapter.fullLogString()

        fun clear() {
            logAdapter.clear()
        }

        fun scrollToBottom() {
            recyclerView.scrollToPosition(logAdapter.itemCount - 1)
        }
    }
