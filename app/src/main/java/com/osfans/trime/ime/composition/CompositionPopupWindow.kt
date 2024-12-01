// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.PopupWindow
import androidx.core.math.MathUtils
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.enums.PopupPosition
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp

@InputScope
@Inject
class CompositionPopupWindow(
    private val ctx: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val bar: QuickBar,
) : InputBroadcastReceiver {
    val root = CandidatesView(ctx, rime, theme)

    // 悬浮窗口是否可移動
    private val isPopupWindowMovable = theme.generalStyle.layout.movable

    private var popupWindowX = 0
    private var popupWindowY = 0 // 悬浮床移动座標

    // 候選窗與邊緣空隙
    private val popupMargin = theme.generalStyle.layout.spacing

    // 悬浮窗与屏幕两侧的间距
    private val popupMarginH = theme.generalStyle.layout.realMargin

    // 悬浮窗口彈出位置
    private var popupWindowPos = PopupPosition.fromString(theme.generalStyle.layout.position)

    private val mPopupWindow =
        PopupWindow(root).apply {
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                windowLayoutType =
                    WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            setBackgroundDrawable(
                ColorManager.getDrawable(
                    ctx,
                    "text_back_color",
                    theme.generalStyle.layout.border,
                    "border_color",
                    theme.generalStyle.layout.roundCorner,
                    theme.generalStyle.layout.alpha,
                ),
            )
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            elevation =
                ctx.dp(
                    theme.generalStyle.layout.elevation
                        .toFloat(),
                )
        }

    var isCursorUpdated = false // 光標是否移動

    private val anchorPosition = RectF()
    private val mPopupHandler = Handler(Looper.getMainLooper())

    private val mPopupTimer =
        Runnable {
            if (bar.view.windowToken == null) return@Runnable
            bar.view.let { anchor ->
                var x: Int
                var y: Int
                val (_, anchorY) =
                    intArrayOf(0, 0).also {
                        anchor.getLocationInWindow(it)
                    }
                root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                root.requestLayout()
                val selfWidth = root.width
                val selfHeight = root.height

                val minX = anchor.dp(popupMarginH)
                val minY = anchor.dp(popupMargin)
                val maxX = anchor.width - selfWidth - minX
                val maxY = anchorY - selfHeight - minY
                when (popupWindowPos) {
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
                    PopupPosition.DRAG -> {
                        x = popupWindowX
                        y = popupWindowY
                    }
                    PopupPosition.FIXED, PopupPosition.BOTTOM_LEFT -> {
                        x = minX
                        y = maxY
                    }
                    PopupPosition.LEFT -> {
                        x = anchorPosition.left.toInt()
                        y = anchorPosition.bottom.toInt() + popupMargin
                    }
                    PopupPosition.LEFT_UP -> {
                        x = anchorPosition.left.toInt()
                        y = anchorPosition.top.toInt() - selfHeight - popupMargin
                    }
                    PopupPosition.RIGHT -> {
                        x = anchorPosition.right.toInt()
                        y = anchorPosition.bottom.toInt() + popupMargin
                    }
                    PopupPosition.RIGHT_UP -> {
                        x = anchorPosition.right.toInt()
                        y = anchorPosition.top.toInt() - selfHeight - popupMargin
                    }
                    else -> {
                        x = minX
                        y = maxY
                    }
                }
                if (!isWinFixed() || isCursorUpdated) {
                    x = MathUtils.clamp(x, minX, maxX)
                    y = MathUtils.clamp(y, minY, maxY)
                }
                if (!mPopupWindow.isShowing) {
                    mPopupWindow.showAtLocation(anchor, Gravity.START or Gravity.TOP, x, y)
                } else {
                    /* must use the width and height of popup window itself here directly,
                     * otherwise the width and height cannot be updated! */
                    mPopupWindow.update(x, y, -1, -1)
                }
            }
        }

    fun isWinFixed(): Boolean =
        Build.VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP ||
            popupWindowPos !== PopupPosition.LEFT &&
            popupWindowPos !== PopupPosition.RIGHT &&
            popupWindowPos !== PopupPosition.LEFT_UP &&
            popupWindowPos !== PopupPosition.RIGHT_UP

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        if (ctx.composition.length > 0) {
            root.update(ctx)
            updateCompositionView()
        } else {
            hideCompositionView()
        }
    }

    fun hideCompositionView() {
        mPopupWindow.dismiss()
        mPopupHandler.removeCallbacks(mPopupTimer)
        decorLocationUpdated = false
    }

    private fun updateCompositionView() {
        if (isPopupWindowMovable == "once") {
            popupWindowPos = PopupPosition.fromString(theme.generalStyle.layout.position)
        }
        mPopupHandler.post(mPopupTimer)
    }

    private val decorLocation = floatArrayOf(0f, 0f)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        val (dX, dY) =
            intArrayOf(0, 0).also {
                service.window.window!!
                    .decorView
                    .getLocationOnScreen(it)
            }
        decorLocation[0] = dX.toFloat()
        decorLocation[1] = dY.toFloat()
        decorLocationUpdated = true
    }

    fun updateCursorAnchorInfo(info: CursorAnchorInfo) {
        if (!isWinFixed()) {
            val bounds = info.getCharacterBounds(0)
            // update mPopupRectF
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
                updateDecorLocation()
            }
            val (dX, dY) = decorLocation
            anchorPosition.offset(-dX, -dY)
        }
    }
}
