/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView

@SuppressLint("AppCompatCustomView")
class PreeditTextView
    @JvmOverloads
    constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
    ) : TextView(context, attributeSet) {
        private var onCursorMove: ((Int) -> Unit)? = null

        fun setOnCursorMoveListener(listener: ((Int) -> Unit)) {
            onCursorMove = listener
        }

        private var touchX: Int = 0
        private var newSel: CharSequence = ""

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = getOffsetForPosition(event.x, event.y)
                    newSel = text.subSequence(0, touchX).replace("\\s".toRegex(), "")
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    onCursorMove?.invoke(newSel.length)
                    touchX = 0
                    newSel = ""
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    touchX = 0
                    newSel = ""
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
