/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.KeyModifier
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.ScancodeMapping
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.Speech
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.dialog.AvailableSchemaPickerDialog
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.ime.symbol.TabManager
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.KeySoundEffectPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.ShortcutUtils
import com.osfans.trime.util.ShortcutUtils.openCategory
import com.osfans.trime.util.isAsciiPrintable
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.systemservices.inputMethodManager
import timber.log.Timber

@InputScope
@Inject
class CommonKeyboardActionListener(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val inputView: InputView,
    private val liquidKeyboard: LiquidKeyboard,
    private val windowManager: BoardWindowManager,
) {
    companion object {
        /** Pattern for braced key event like `{Left}`, `{Right}`, etc. */
        private val BRACED_KEY_EVENT = """^(\{[^{}]+\}).*$""".toRegex()

        /** Pattern for braced key event to capture `{Escape}` as group 2 */
        private val BRACED_KEY_EVENT_WITH_ESCAPE = """^((\{Escape\})?[^{}]+).*$""".toRegex()
    }

    private val prefs = AppPrefs.defaultInstance()

    private var shouldReleaseKey: Boolean = false

    private fun showDialog(dialog: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                inputView.showDialog(dialog(api))
            }
        }
    }

    private fun showThemePicker() {
        showDialog {
            ThemePickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showColorPicker() {
        showDialog {
            ColorPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showSoundEffectPicker() {
        showDialog {
            KeySoundEffectPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showAvailableSchemaPicker() {
        showDialog { api ->
            AvailableSchemaPickerDialog.build(api, service.lifecycleScope, context)
        }
    }

    private fun showEnabledSchemaPicker() {
        showDialog { api ->
            EnabledSchemaPickerDialog.build(api, service.lifecycleScope, context) {
                setPositiveButton(R.string.enable_schemata) { _, _ ->
                    showAvailableSchemaPicker()
                }
                setNegativeButton(R.string.set_ime) { _, _ ->
                    ShortcutUtils.launchMainActivity(context)
                }
            }
        }
    }

    val listener by lazy {
        object : KeyboardActionListener {
            override fun onPress(keyEventCode: Int) {
                InputFeedbackManager.run {
                    keyPressVibrate(service.window.window!!.decorView)
                    keyPressSound(keyEventCode)
                    keyPressSpeak(keyEventCode)
                }
            }

            override fun onRelease(keyEventCode: Int) {
                if (shouldReleaseKey) {
                    // FIXME: 释放按键可能不对
                    val value = RimeKeyMapping.keyCodeToVal(keyEventCode)
                    if (value != RimeKeyMapping.RimeKey_VoidSymbol) {
                        service.postRimeJob {
                            processKey(value, KeyModifier.Release.modifier)
                        }
                    }
                }
            }

            override fun onAction(action: KeyAction) {
                when (action.code) {
                    KeyEvent.KEYCODE_SWITCH_CHARSET -> { // Switch status
                        rime.launchOnReady { api ->
                            service.lifecycleScope.launch {
                                val option = action.toggle.ifEmpty { return@launch }
                                val status = api.getRuntimeOption(option)
                                api.setRuntimeOption(option, !status)

                                api.commitComposition()
                            }
                        }
                    }
                    KeyEvent.KEYCODE_LANGUAGE_SWITCH -> { // Switch IME
                        if (action.select == ".next") {
                            service.switchToNextIme()
                        } else if (action.select.isNotEmpty()) {
                            service.switchToPrevIme()
                        } else {
                            inputMethodManager.showInputMethodPicker()
                        }
                    }
                    KeyEvent.KEYCODE_FUNCTION -> { // Command Express
                        // Comments from trime.yaml:
                        // %s或者%1$s爲當前字符
                        // %2$s爲當前輸入的編碼
                        // %3$s爲光標前字符
                        // %4$s爲光標前所有字符
                        var arg = action.option
                        val activeTextRegex = Regex(".*%(\\d*)\\$" + "s.*")
                        if (arg.matches(activeTextRegex)) {
                            var activeTextMode =
                                arg.replaceFirst(activeTextRegex, "$1").toDouble().toInt()
                            if (activeTextMode < 1) {
                                activeTextMode = 1
                            }
                            val activeText = service.getActiveText(activeTextMode)
                            arg =
                                String.format(
                                    arg,
                                    service.lastCommittedText,
                                    Rime.getRimeRawInput() ?: "",
                                    activeText,
                                    activeText,
                                )
                        }

                        when (action.command) {
                            "liquid_keyboard" -> {
                                val target =
                                    when {
                                        arg.matches("-?\\d+".toRegex()) -> arg.toInt()
                                        arg.matches("[A-Z]+".toRegex()) -> {
                                            val type = SymbolBoardType.valueOf(arg)
                                            TabManager.tabTags.indexOfFirst { it.type == type }
                                        }
                                        else -> TabManager.tabTags.indexOfFirst { it.text == arg }
                                    }
                                if (target >= 0) {
                                    windowManager.attachWindow(LiquidKeyboard)
                                    liquidKeyboard.select(target)
                                } else {
                                    windowManager.attachWindow(KeyboardWindow)
                                }
                            }
                            "paste_by_char" -> service.pasteByChar()
                            "set_color_scheme" -> ColorManager.setColorScheme(arg)
                            else -> {
                                ShortcutUtils.call(service, action.command, arg)?.let {
                                    service.commitText(it)
                                    service.updateComposing()
                                }
                            }
                        }
                    }
                    KeyEvent.KEYCODE_VOICE_ASSIST -> Speech(service).startListening() // Speech Recognition
                    KeyEvent.KEYCODE_SETTINGS -> { // Settings
                        when (action.option) {
                            "theme" -> showThemePicker()
                            "color" -> showColorPicker()
                            "schema" -> showAvailableSchemaPicker()
                            "sound" -> showSoundEffectPicker()
                            else -> ShortcutUtils.launchMainActivity(service)
                        }
                    }
                    KeyEvent.KEYCODE_PROG_RED -> showColorPicker()
                    KeyEvent.KEYCODE_MENU -> showEnabledSchemaPicker()
                    else -> {
                        if (action.modifier == 0 && KeyboardSwitcher.currentKeyboard.isOnlyShiftOn) {
                            if (action.code == KeyEvent.KEYCODE_SPACE && prefs.keyboard.hookShiftSpace) {
                                onKey(action.code, 0)
                                return
                            } else if (action.code >= KeyEvent.KEYCODE_0 &&
                                action.code <= KeyEvent.KEYCODE_9 &&
                                prefs.keyboard.hookShiftNum
                            ) {
                                onKey(action.code, 0)
                                return
                            } else if (prefs.keyboard.hookShiftSymbol) {
                                if (action.code >= KeyEvent.KEYCODE_GRAVE &&
                                    action.code <= KeyEvent.KEYCODE_SLASH ||
                                    action.code == KeyEvent.KEYCODE_COMMA ||
                                    action.code == KeyEvent.KEYCODE_PERIOD
                                ) {
                                    onKey(action.code, 0)
                                    return
                                }
                            }
                        }
                        if (action.modifier == 0) {
                            onKey(action.code, KeyboardSwitcher.currentKeyboard.modifier)
                        } else {
                            onKey(action.code, action.modifier)
                        }
                    }
                }
            }

            override fun onKey(
                keyEventCode: Int,
                metaState: Int,
            ) {
                shouldReleaseKey = false
                val value =
                    RimeKeyMapping
                        .keyCodeToVal(keyEventCode)
                        .takeIf { it != RimeKeyMapping.RimeKey_VoidSymbol }
                        ?: Rime.getRimeKeycodeByName(Keycode.keyNameOf(keyEventCode))
                val modifiers = KeyModifiers.fromMetaState(metaState).modifiers
                val code = ScancodeMapping.keyCodeToScancode(keyEventCode)
                service.postRimeJob {
                    if (processKey(value, modifiers, code)) {
                        shouldReleaseKey = true
                        Timber.d("handleKey: processKey")
                        return@postRimeJob
                    }
                    if (service.hookKeyboard(keyEventCode, metaState)) {
                        Timber.d("handleKey: hook")
                        return@postRimeJob
                    }
                    if (context.openCategory(keyEventCode)) {
                        Timber.d("handleKey: openCategory")
                        return@postRimeJob
                    }
                    shouldReleaseKey = false

                    when (keyEventCode) {
                        KeyEvent.KEYCODE_ENTER -> service.handleReturnKey()
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> service.requestHideSelf(0)
                        else -> {
                            // 小键盘自动增加锁定
                            if (keyEventCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                                service.sendDownUpKeyEvent(
                                    keyEventCode,
                                    metaState or KeyEvent.META_NUM_LOCK_ON,
                                )
                            }
                        }
                    }
                }
            }

            override fun onText(text: CharSequence) {
                if (!text.first().isAsciiPrintable() && Rime.isComposing) {
                    service.postRimeJob {
                        commitComposition()
                    }
                }
                var sequence = text
                while (sequence.isNotEmpty()) {
                    var slice: String
                    when {
                        BRACED_KEY_EVENT_WITH_ESCAPE.matches(sequence) -> {
                            slice = BRACED_KEY_EVENT_WITH_ESCAPE.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            // FIXME: rime will not handle the key sequence when
                            //  ascii_mode is on, there may be a better solution
                            //  for this.
                            if (Rime.simulateKeySequence(slice)) {
                                if (Rime.isAsciiMode) {
                                    service.commitText(slice)
                                }
                            } else {
                                service.commitText(slice)
                            }
                        }
                        BRACED_KEY_EVENT.matches(sequence) -> {
                            slice = BRACED_KEY_EVENT.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            onAction(KeyActionManager.getAction(slice))
                        }
                        else -> {
                            slice = sequence.first().toString()
                            onAction(KeyActionManager.getAction(slice))
                        }
                    }
                    sequence = sequence.substring(slice.length)
                }
                shouldReleaseKey = false
            }
        }
    }
}
