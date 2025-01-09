// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.core.Rime
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.util.virtualKeyCharacterMap
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import splitties.bitflags.hasFlag

/** [按鍵][Key]的各種事件（單擊、長按、滑動等）  */
class KeyAction(
    raw: String,
) {
    var code = 0
        private set
    var modifier = 0
        private set
    var command: String = ""
        private set
    var option: String = ""
        private set
    var select: String = ""
        private set
    var toggle: String = ""
        private set
    var commit: String = ""
        private set
    var shiftLock: String = ""
        private set
    var isFunctional = false
        private set
    var isRepeatable = false
        private set
    var isSticky = false
        private set

    private var text: String = ""
    private var label: String = ""
    private var shiftLabel = ""
    private var preview: String = ""
    private var states: List<String>? = null

    private val hookShiftNum get() = AppPrefs.defaultInstance().keyboard.hookShiftNum
    private val hookShiftSymbol get() = AppPrefs.defaultInstance().keyboard.hookShiftSymbol

    private fun adjustCase(
        str: String,
        keyboard: Keyboard,
    ): String =
        if (str.length == 1 && (keyboard.isShifted || (!Rime.isAsciiMode && keyboard.isLabelUppercase))) {
            str.uppercase()
        } else {
            str
        }

    fun getLabel(keyboard: Keyboard): String {
        val state = states?.get(if (Rime.getOption(toggle)) 1 else 0)
        if (state != null) return state
        if (keyboard.isOnlyShiftOn) {
            if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && !hookShiftNum) {
                return adjustCase(shiftLabel, keyboard)
            }
            if (code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH ||
                code == KeyEvent.KEYCODE_COMMA ||
                code == KeyEvent.KEYCODE_PERIOD
            ) {
                if (!hookShiftSymbol) return adjustCase(shiftLabel, keyboard)
            }
        } else if ((modifier or keyboard.modifier).hasFlag(KeyEvent.META_SHIFT_ON)) {
            return adjustCase(shiftLabel, keyboard)
        }
        return adjustCase(label, keyboard)
    }

    fun getText(keyboard: Keyboard): String =
        if (text.isNotEmpty()) {
            adjustCase(text, keyboard)
        } else if (keyboard.isShifted && code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && modifier == 0) {
            adjustCase(label, keyboard)
        } else {
            text
        }

    fun getPreview(keyboard: Keyboard): String = preview.ifEmpty { getLabel(keyboard) }

    init {
        val unbraced = raw.removeSurrounding("{", "}")
        var handled = false
        // match like: { x: "{Control+a}" }
        if (raw.matches(BRACED_STR)) {
            val (c, m) = Keycode.parseSend(unbraced)
            if (c != KeyEvent.KEYCODE_UNKNOWN || m > 0) {
                code = c
                modifier = m
                handled = true
            } else {
                // match: { x: { commit: a, text: b, label: c } }
                val action = decodeMapFromString(raw)
                if (action.isNotEmpty()) {
                    commit = action["commit"] ?: ""
                    text = action["text"] ?: ""
                    label = action["label"] ?: ""
                    handled = true
                }
            }
        }
        if (!handled) {
            val theme = ThemeManager.activeTheme
            // match like: { x: BackSpace } -> preset_keys/BackSpace: {..., send: BackSpace }
            if (theme.presetKeys!!.containsKey(unbraced)) {
                theme.presetKeys!![unbraced]!!.configMap.let {
                    command = it.getValue("command")?.getString() ?: ""
                    option = it.getValue("option")?.getString() ?: ""
                    select = it.getValue("select")?.getString() ?: ""
                    toggle = it.getValue("toggle")?.getString() ?: ""
                    label = it.getValue("label")?.getString() ?: ""
                    preview = it.getValue("preview")?.getString() ?: ""
                    shiftLock = it.getValue("shift_lock")?.getString() ?: ""
                    commit = it.getValue("commit")?.getString() ?: ""
                    text = it.getValue("text")?.getString() ?: ""
                    isSticky = it.getValue("sticky")?.getBool() ?: false
                    isRepeatable = it.getValue("repeatable")?.getBool() ?: false
                    isFunctional = it.getValue("functional")?.getBool() ?: true

                    states =
                        runCatching {
                            it["states"]?.configList?.decode(ListSerializer(String.serializer()))
                        }.getOrNull()

                    val send = it.getValue("send")?.getString()
                    if (!send.isNullOrEmpty()) {
                        val (c, m) = Keycode.parseSend(send)
                        code = c
                        modifier = m
                    } else if (command.isNotEmpty()) {
                        code = KeyEvent.KEYCODE_FUNCTION
                    }

                    if (label.isEmpty()) {
                        if (code == KeyEvent.KEYCODE_SPACE) {
                            label = Rime.currentSchemaName
                        } else if (code != KeyEvent.KEYCODE_UNKNOWN) {
                            label = Keycode.getDisplayLabel(code, modifier)
                        }
                    }
                }
            } else {
                // match like: { x: 1 } or { x: q } ...
                code = Keycode.keyCodeOf(unbraced)
                // match like: { x: "(){Left}" } (key sequence to simulate)
                if (unbraced.isNotEmpty() && !Keycode.isStdKey(code)) {
                    text = raw
                    label = raw.replace(BRACED_STR, "")
                } else {
                    if (label.isEmpty()) {
                        if (code == KeyEvent.KEYCODE_SPACE) {
                            label = Rime.currentSchemaName
                        } else if (code != KeyEvent.KEYCODE_UNKNOWN) {
                            label = Keycode.getDisplayLabel(code, modifier)
                        }
                    }
                }
            }
            shiftLabel = label
            if (Keycode.isStdKey(code)) { // Android keycode区域
                if (virtualKeyCharacterMap.isPrintingKey(code)) {
                    val charCode = virtualKeyCharacterMap.get(code, modifier or KeyEvent.META_SHIFT_ON)
                    if (charCode > 0) {
                        shiftLabel = charCode.toChar().toString()
                    }
                }
            }
        }
    }

    companion object {
        private val BRACED_STR = Regex("""\{[^{}]+\}""")
        private val KV_PATTERN = Regex("""(\\w+)=(\\w+)""")

        private fun decodeMapFromString(str: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val trimmed = str.removeSurrounding("{", "}")
            val matches = KV_PATTERN.findAll(trimmed)
            for (match in matches) {
                val (_, k, v) = match.groupValues
                map[k] = v
            }
            return map
        }
    }
}
