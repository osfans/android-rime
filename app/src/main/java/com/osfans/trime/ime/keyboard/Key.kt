// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import com.osfans.trime.core.Rime.Companion.hasLeft
import com.osfans.trime.core.Rime.Companion.hasMenu
import com.osfans.trime.core.Rime.Companion.isAsciiMode
import com.osfans.trime.core.Rime.Companion.isComposing
import com.osfans.trime.core.Rime.Companion.showAsciiPunch
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.util.CollectionUtils.obtainBoolean
import com.osfans.trime.util.CollectionUtils.obtainFloat
import com.osfans.trime.util.CollectionUtils.obtainString
import java.text.MessageFormat

/** [鍵盤][Keyboard]中的各個按鍵，包含單擊、長按、滑動等多種[事件][KeyAction]  */
@Suppress(
    "ktlint:standard:property-naming",
    "ktlint:standard:value-argument-comment",
)
class Key(
    private val mKeyboard: Keyboard,
) {
    var keyActions = arrayOfNulls<KeyAction>(EVENT_NUM)

    var edgeFlags = 0
    private var sendBindings = true

    var width = 0

    var height = 0

    var gap = 0

    var row = 0

    var column = 0
    private var label = ""
    var hint: String = ""
        private set
    private lateinit var keyMap: Map<String, Any?>
    private val keyBackColor get() = ColorManager.getDrawable(keyMap, "key_back_color")
    private val hilitedKeyBackColor get() = ColorManager.getDrawable(keyMap, "hilited_key_back_color")

    private val keyTextColor get() = ColorManager.getColor(keyMap, "key_text_color")
    private val keySymbolColor get() = ColorManager.getColor(keyMap, "key_symbol_color")
    private val hilitedKeyTextColor get() = ColorManager.getColor(keyMap, "hilited_key_text_color")
    private val hilitedKeySymbolColor get() = ColorManager.getColor(keyMap, "hilited_key_symbol_color")

    var keyTextSize: Float = 0f
        private set
    var symbolTextSize: Float = 0f
        private set
    var roundCorner: Float = 0f
        private set
    var keyTextOffsetX = 0f
        get() = field + keyOffsetX
    var keyTextOffsetY = 0f
        get() = field + keyOffsetY
    var keySymbolOffsetX = 0f
        get() = field + keyOffsetX
    var keySymbolOffsetY = 0f
        get() = field + keyOffsetY
    var keyHintOffsetX = 0f
        get() = field + keyOffsetX
    var keyHintOffsetY = 0f
        get() = field + keyOffsetY
    var keyPressOffsetX = 0
    var keyPressOffsetY = 0

    @JvmField
    var x = 0

    @JvmField
    var y = 0
    var isPressed = false
        private set
    var isOn = false
        private set

    private var labelSymbol = ""

    /**
     * Create an empty key with no attributes.
     *
     * @param parent 按鍵所在的[鍵盤][Keyboard]
     * @param externalKeyMap 從YAML中解析得到的按键定义
     */
    constructor(parent: Keyboard, externalKeyMap: Map<String, Any?>) : this(parent) {
        keyMap = externalKeyMap
        var hasComposingKey = false
        for (behavior in KeyBehavior.entries) {
            val behaviorName = behavior.name.lowercase()
            val what = obtainString(externalKeyMap, behaviorName)
            if (what.isNotEmpty()) {
                keyActions[behavior.ordinal] = KeyActionManager.getAction(what)
                if (behavior.ordinal < KeyBehavior.COMBO.ordinal) hasComposingKey = true
            } else if (behavior == KeyBehavior.CLICK) {
                keyActions[behavior.ordinal] = KeyActionManager.getAction("")
            }
        }
        if (hasComposingKey) mKeyboard.composingKeys.add(this)
        label = obtainString(externalKeyMap, "label")
        labelSymbol = obtainString(externalKeyMap, "label_symbol")
        hint = obtainString(externalKeyMap, "hint")
        if (externalKeyMap.containsKey("send_bindings")) {
            sendBindings = obtainBoolean(externalKeyMap, "send_bindings", true)
        } else if (!hasComposingKey) {
            sendBindings = false
        }
        mKeyboard.setModiferKey(this.code, this)
        keyTextSize = obtainFloat(externalKeyMap, "key_text_size")
        symbolTextSize = obtainFloat(externalKeyMap, "symbol_text_size")
        roundCorner = obtainFloat(externalKeyMap, "round_corner")
    }

    fun setOn(on: Boolean): Boolean {
        isOn = if (on && isOn) false else on
        return isOn
    }

    private val keyOffsetX: Int
        get() = if (isPressed) keyPressOffsetX else 0
    private val keyOffsetY: Int
        get() = if (isPressed) keyPressOffsetY else 0

    fun getBackColorForState(drawableState: IntArray): Drawable? =
        when (drawableState) {
            KEY_STATE_NORMAL, KEY_STATE_OFF_NORMAL -> keyBackColor
            KEY_STATE_PRESSED, KEY_STATE_OFF_PRESSED -> hilitedKeyBackColor
            else -> null
        }

    fun getTextColorForState(drawableState: IntArray): Int? =
        when (drawableState) {
            KEY_STATE_NORMAL, KEY_STATE_OFF_NORMAL -> keyTextColor
            KEY_STATE_PRESSED, KEY_STATE_OFF_PRESSED -> hilitedKeyTextColor
            else -> null
        }

    fun getSymbolColorForState(drawableState: IntArray): Int? =
        when (drawableState) {
            KEY_STATE_NORMAL, KEY_STATE_OFF_NORMAL -> keySymbolColor
            KEY_STATE_PRESSED, KEY_STATE_OFF_PRESSED -> hilitedKeySymbolColor
            else -> null
        }

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or state.
     *
     * @see .onReleased
     */
    fun onPressed() {
        isPressed = true
    }

    /**
     * Changes the pressed state of the key. If it is a sticky key, it will also change the toggled
     * state of the key if the finger was release inside.
     *
     * @see .onPressed
     */
    fun onReleased() {
        isPressed = false
        if (click!!.isSticky) isOn = !isOn
    }

    /**
     * Detects if a point falls inside this key.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls inside the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be inside the
     * key.
     */
    fun isInside(
        x: Int,
        y: Int,
    ): Boolean {
        val leftEdge = edgeFlags and Keyboard.EDGE_LEFT > 0
        val rightEdge = edgeFlags and Keyboard.EDGE_RIGHT > 0
        val topEdge = edgeFlags and Keyboard.EDGE_TOP > 0
        val bottomEdge = edgeFlags and Keyboard.EDGE_BOTTOM > 0
        return (
            (x >= this.x || leftEdge && x <= this.x + width) &&
                (x < this.x + width || rightEdge && x >= this.x) &&
                (y >= this.y || topEdge && y <= this.y + height) &&
                (y < this.y + height || bottomEdge && y >= this.y)
        )
    }

    /**
     * Returns the square of the distance between the center of the key and the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the center of the key
     */
    fun squaredDistanceFrom(
        x: Int,
        y: Int,
    ): Int {
        val xDist = this.x + width / 2 - x
        val yDist = this.y + height / 2 - y
        return xDist * xDist + yDist * yDist
    }

    private val isTrimeModifierKey: Boolean
        // Trime把function键消费掉了，因此键盘只处理function键以外的修饰键
        get() = isTrimeModifierKey(this.code)

    val currentDrawableState: IntArray
        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         *
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable.setState
         */
        get() {
            val isShifted = isTrimeModifierKey && mKeyboard.hasModifier(modifierKeyOnMask)
            val states =
                if (isShifted || isOn) {
                    if (isPressed) KEY_STATE_ON_PRESSED else KEY_STATE_ON_NORMAL
                } else if (click!!.isSticky || click!!.isFunctional) {
                    if (isPressed) KEY_STATE_OFF_PRESSED else KEY_STATE_OFF_NORMAL
                } else {
                    if (isPressed) KEY_STATE_PRESSED else KEY_STATE_NORMAL
                }

            // only for modiferKey debug
            if (isTrimeModifierKey) {
                mKeyboard.printModifierKeyState(
                    MessageFormat.format(
                        "getCurrentDrawableState() Key={0} states={1} on={2} isShifted={3} pressed={4} sticky={5}",
                        getLabel(),
                        listOf(*KEY_STATES).indexOf(states),
                        isOn,
                        isShifted,
                        isPressed,
                        click!!.isSticky,
                    ),
                )
            }
            return states
        }
    val modifierKeyOnMask: Int
        get() = getModifierKeyOnMask(this.code)

    private fun getModifierKeyOnMask(keycode: Int): Int {
        if (keycode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keycode == KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return KeyEvent.META_SHIFT_ON
        }
        if (keycode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keycode == KeyEvent.KEYCODE_CTRL_RIGHT
        ) {
            return KeyEvent.META_CTRL_ON
        }
        if (keycode == KeyEvent.KEYCODE_META_LEFT ||
            keycode == KeyEvent.KEYCODE_META_RIGHT
        ) {
            return KeyEvent.META_META_ON
        }
        if (keycode == KeyEvent.KEYCODE_ALT_LEFT ||
            keycode == KeyEvent.KEYCODE_ALT_RIGHT
        ) {
            return KeyEvent.META_ALT_ON
        }
        return if (keycode == KeyEvent.KEYCODE_SYM) KeyEvent.META_SYM_ON else 0
    }

    val isShift: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_SHIFT_LEFT || c == KeyEvent.KEYCODE_SHIFT_RIGHT
        }
    val isShiftLock: Boolean
        // Shift、Ctrl、Alt、Meta等修饰键在点击时是否触发锁定
        get() {
            val s = click!!.shiftLock
            // shift_lock #ascii_long: 英文長按中文單按鎖定, long: 長按鎖定, click: 單按鎖定
            if ("long" == s) return false
            if ("click" == s) return true
            return if ("ascii_long" == s) !isAsciiMode else false
        }

    /**
     * @param behavior 同文按键模式（点击/长按/滑动）
     * @return
     */
    fun sendBindings(behavior: KeyBehavior): Boolean {
        var e: KeyAction? = null
        if (behavior != KeyBehavior.CLICK) e = keyActions[behavior.ordinal]
        if (e != null) return true
        if (keyActions[KeyBehavior.ASCII.ordinal] != null && isAsciiMode) return false
        if (sendBindings) {
            if (keyActions[KeyBehavior.PAGING.ordinal] != null && hasLeft()) return true
            if (keyActions[KeyBehavior.HAS_MENU.ordinal] != null && hasMenu()) return true
            if (keyActions[KeyBehavior.COMPOSING.ordinal] != null && isComposing) return true
        }
        return false
    }

    private val keyAction: KeyAction?
        get() {
            if (keyActions[KeyBehavior.ASCII.ordinal] != null && isAsciiMode) {
                return keyActions[KeyBehavior.ASCII.ordinal]
            }
            if (keyActions[KeyBehavior.PAGING.ordinal] != null && hasLeft()) {
                return keyActions[KeyBehavior.PAGING.ordinal]
            }
            if (keyActions[KeyBehavior.HAS_MENU.ordinal] != null && hasMenu()) {
                return keyActions[KeyBehavior.HAS_MENU.ordinal]
            }
            return if (keyActions[KeyBehavior.COMPOSING.ordinal] != null && isComposing) {
                keyActions[KeyBehavior.COMPOSING.ordinal]
            } else {
                click
            }
        }
    val click: KeyAction?
        get() = keyActions[KeyBehavior.CLICK.ordinal]
    val longClick: KeyAction?
        get() = keyActions[KeyBehavior.LONG_CLICK.ordinal]

    fun hasAction(behavior: KeyBehavior): Boolean = keyActions[behavior.ordinal] != null

    fun getAction(behavior: KeyBehavior): KeyAction? {
        var e: KeyAction? = null
        if (behavior != KeyBehavior.CLICK) e = keyActions[behavior.ordinal]
        if (e != null) return e
        if (keyActions[KeyBehavior.ASCII.ordinal] != null && isAsciiMode) {
            return keyActions[KeyBehavior.ASCII.ordinal]
        }
        if (sendBindings) {
            if (keyActions[KeyBehavior.PAGING.ordinal] != null && hasLeft()) {
                return keyActions[KeyBehavior.PAGING.ordinal]
            }
            if (keyActions[KeyBehavior.HAS_MENU.ordinal] != null && hasMenu()) {
                return keyActions[KeyBehavior.HAS_MENU.ordinal]
            }
            if (keyActions[KeyBehavior.COMPOSING.ordinal] != null && isComposing) {
                return keyActions[KeyBehavior.COMPOSING.ordinal]
            }
        }
        return click
    }

    val code: Int
        get() = click!!.code

    fun getCode(behavior: KeyBehavior): Int = getAction(behavior)!!.code

    fun getLabel(): String {
        keyAction
        return if (label.isNotEmpty() &&
            keyAction === click &&
            keyActions[KeyBehavior.ASCII.ordinal] == null &&
            !showAsciiPunch()
        ) {
            label
        } else {
            keyAction!!.getLabel(mKeyboard) // 中文狀態顯示標籤
        }
    }

    fun getPreviewText(behavior: KeyBehavior): String =
        if (behavior == KeyBehavior.CLICK) {
            keyAction!!.getPreview(mKeyboard)
        } else {
            getAction(behavior)!!.getPreview(mKeyboard)
        }

    val symbolLabel: String
        get() {
            if (labelSymbol.isEmpty()) {
                val longClick = longClick
                if (longClick != null) return longClick.getLabel(mKeyboard)
            }
            return labelSymbol
        }

    companion object {
        val KEY_STATE_ON_NORMAL =
            intArrayOf(
                android.R.attr.state_checkable,
                android.R.attr.state_checked,
            )

        val KEY_STATE_ON_PRESSED =
            intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
                android.R.attr.state_checked,
            )

        val KEY_STATE_OFF_NORMAL = intArrayOf(android.R.attr.state_checkable)

        val KEY_STATE_OFF_PRESSED =
            intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
            )

        val KEY_STATE_NORMAL = intArrayOf()

        val KEY_STATE_PRESSED = intArrayOf(android.R.attr.state_pressed)

        val KEY_STATES =
            arrayOf(
                KEY_STATE_ON_PRESSED, // 0    "hilited_on_key_back_color"   锁定时按下的背景
                KEY_STATE_ON_NORMAL, // 1     "on_key_back_color"           锁定时背景
                KEY_STATE_OFF_PRESSED, // 2   "hilited_off_key_back_color"  功能键按下的背景
                KEY_STATE_OFF_NORMAL, // 3    "off_key_back_color"          功能键背景
                KEY_STATE_PRESSED, // 4       "hilited_key_back_color"      按键按下的背景
                KEY_STATE_NORMAL, // 5         "key_back_color"              按键背景
            )

        private val EVENT_NUM = KeyBehavior.entries.size

        @JvmStatic
        fun isTrimeModifierKey(keycode: Int): Boolean = if (keycode == KeyEvent.KEYCODE_FUNCTION) false else KeyEvent.isModifierKey(keycode)
    }
}
