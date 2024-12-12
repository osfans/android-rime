// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.composition

import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.PopupWindow
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.osfans.trime.core.RimeCallback
import com.osfans.trime.core.RimeEvent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.BaseCallbackHandler
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.dimensions.dp
import timber.log.Timber

class ComposingPopupWindow(
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val parentView: View,
) {
    val root = CandidatesView(service, rime, theme)

    var useVirtualKeyboard: Boolean = true

    // 悬浮窗口彈出位置
    private val position by AppPrefs.defaultInstance().candidates.position

    private val window =
        PopupWindow(root).apply {
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                windowLayoutType =
                    WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            setBackgroundDrawable(
                ColorManager.getDrawable(
                    service,
                    "text_back_color",
                    theme.generalStyle.layout.border,
                    "border_color",
                    theme.generalStyle.layout.roundCorner,
                    theme.generalStyle.layout.alpha
                        .toInt(),
                ),
            )
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            elevation =
                root.dp(
                    theme.generalStyle.layout.elevation
                        .toFloat(),
                )
        }

    private val anchorPosition = RectF()

    private val positionUpdater =
        Runnable {
            val x: Int
            val y: Int
            root.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            root.requestLayout()
            val selfWidth = root.width
            val selfHeight = root.height
            val (horizontal, top, _, bottom) = anchorPosition
            val parentWidth = parentView.width
            val parentHeight = parentView.height
            val (_, inputViewHeight) =
                intArrayOf(0, 0)
                    .also { service.inputView?.keyboardView?.getLocationInWindow(it) }

            val minX = 0
            val minY = 0
            val maxX = parentWidth - selfWidth
            val maxY =
                if (useVirtualKeyboard) {
                    inputViewHeight - selfHeight
                } else {
                    parentHeight - selfHeight
                }
            when (position) {
                PopupPosition.TOP_RIGHT -> {
                    x = maxX
                    y = minY
                }
                PopupPosition.TOP_LEFT -> {
                    x = minX
                    y = minY
                }
                PopupPosition.BOTTOM_RIGHT -> {
                    x = maxX
                    y = maxY
                }
                PopupPosition.BOTTOM_LEFT -> {
                    x = minX
                    y = maxY
                }
                PopupPosition.FOLLOW -> {
                    x =
                        if (root.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            val rtlOffset = parentWidth - horizontal
                            if (rtlOffset + selfWidth > parentWidth) selfWidth - parentWidth else -rtlOffset
                        } else {
                            if (horizontal + selfWidth > parentWidth) parentWidth - selfWidth else horizontal
                        }.toInt()
                    y = (if (bottom + selfHeight > parentHeight) top - selfHeight else bottom).toInt()
                }
            }
            window.update(x, y, -1, -1)
        }

    private val baseCallbackHandler =
        object : BaseCallbackHandler(service, rime) {
            override fun handleRimeCallback(it: RimeCallback) {
                if (it is RimeEvent.IpcResponseEvent) {
                    it.data.context?.let ctx@{
                        if (it.composition.length > 0) {
                            root.update(it)
                            Timber.d("Update! Ready to showup")
                            updatePosition()
                        } else {
                            dismiss()
                        }
                    }
                }
            }
        }

    var handleCallback: Boolean
        get() = baseCallbackHandler.handleCallback
        set(value) {
            baseCallbackHandler.handleCallback = value
        }

    fun cancelJob() {
        dismiss()
        baseCallbackHandler.cancelJob()
    }

    fun dismiss() {
        window.dismiss()
        root.removeCallbacks(positionUpdater)
        decorLocationUpdated = false
    }

    private fun updatePosition() {
        window.showAtLocation(parentView, Gravity.NO_GRAVITY, 0, 0)
        root.post(positionUpdater)
    }

    private val decorLocation = floatArrayOf(0f, 0f)
    private var decorLocationUpdated = false

    private fun updateDecorLocation(decorView: View) {
        val (dX, dY) =
            intArrayOf(0, 0).also { decorView.getLocationOnScreen(it) }
        decorLocation[0] = dX.toFloat()
        decorLocation[1] = dY.toFloat()
        decorLocationUpdated = true
    }

    fun updateCursorAnchorInfo(
        info: CursorAnchorInfo,
        decorView: View,
    ) {
        val bounds = info.getCharacterBounds(0)
        // update anchorPosition
        if (bounds == null) {
            // composing is disabled in target app or trime settings
            // use the position of the insertion marker instead
            anchorPosition.top = info.insertionMarkerTop
            anchorPosition.left = info.insertionMarkerHorizontal
            anchorPosition.bottom = info.insertionMarkerBottom
            anchorPosition.right = info.insertionMarkerHorizontal
        } else {
            // for different writing system (e.g. right to left languages),
            // we have to calculate the correct RectF
            val horizontal = if (root.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition.top = bounds.top
            anchorPosition.left = horizontal
            anchorPosition.bottom = bounds.bottom
            anchorPosition.right = horizontal
        }
        info.matrix.mapRect(anchorPosition)
        // avoid calling `decorView.getLocationOnScreen` repeatedly
        if (!decorLocationUpdated) {
            updateDecorLocation(decorView)
        }
        val (dX, dY) = decorLocation
        anchorPosition.offset(-dX, -dY)
    }
}
