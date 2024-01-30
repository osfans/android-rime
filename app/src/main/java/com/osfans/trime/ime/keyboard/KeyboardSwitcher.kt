package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.appContext
import timber.log.Timber

/** Manages [Keyboard]s and their status. **/
object KeyboardSwitcher {
    var currentId: Int = 0
    var lastId: Int = 0
    var lastLockId: Int = 0

    private var currentDisplayWidth: Int = 0
    private val keyboardPrefs = KeyboardPrefs()

    private val theme = ThemeManager.activeTheme
    lateinit var availableKeyboardIds: List<String>
    lateinit var availableKeyboards: List<Keyboard>

    /** To get current keyboard instance. **/
    @JvmStatic
    val currentKeyboard: Keyboard
        get() {
            if (currentId >= availableKeyboards.size) currentId = 0
            return availableKeyboards[currentId]
        }

    init {
        newOrReset()
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun newOrReset() {
        Timber.d("Switching keyboard back to .default ...")
        availableKeyboardIds = (theme.style.getObject("keyboards") as? List<String>)
            ?.map { theme.keyboards.remapKeyboardId(it) }?.distinct() ?: listOf()

        availableKeyboards =
            availableKeyboardIds.map {
                try {
                    Keyboard(theme.keyboards.remapKeyboardId(it))
                } catch (e: Exception) {
                    Keyboard("default")
                }
            }
    }

    fun switchKeyboard(name: String?) {
        val idx =
            when (name) {
                ".default" -> 0
                ".prior" -> currentId - 1
                ".next" -> currentId + 1
                ".last" -> lastId
                ".last_lock" -> lastLockId
                ".ascii" -> {
                    val asciiKeyboard = availableKeyboards[currentId].asciiKeyboard
                    if (asciiKeyboard.isNullOrEmpty()) {
                        currentId
                    } else {
                        availableKeyboardIds.indexOf(asciiKeyboard)
                    }
                }
                else -> {
                    if (name.isNullOrEmpty()) {
                        if (availableKeyboards[currentId].isLock) currentId else lastLockId
                    } else {
                        availableKeyboardIds.indexOf(name)
                    }
                }
            }
        val i =
            if (keyboardPrefs.isLandscapeMode()) {
                mapToLandscapeKeyboardIdx(idx)
            } else {
                idx
            }

        Timber.d("Mapped from %d to %d", idx, i)

        val deviceKeyboard = appContext.resources.configuration.keyboard
        if (currentId >= 0 && availableKeyboards[currentId].isLock) {
            lastLockId = currentId
        }
        lastId = currentId

        currentId = if (i >= availableKeyboardIds.size || i < 0) 0 else i
        if ("mini" in availableKeyboardIds) {
            val mini = availableKeyboardIds.indexOf("mini")
            currentId =
                if (AppPrefs.defaultInstance().themeAndColor.useMiniKeyboard && deviceKeyboard != Configuration.KEYBOARD_NOKEYS) {
                    if (currentId == 0) mini else currentId
                } else {
                    if (currentId == mini) 0 else currentId
                }
        }

        Timber.i(
            "Switched keyboard from ${availableKeyboardIds[lastId]} " +
                "to ${availableKeyboardIds[currentId]} (deviceKeyboard=$deviceKeyboard).",
        )
    }

    private fun mapToLandscapeKeyboardIdx(idx: Int): Int {
        if (idx < 0 || idx > availableKeyboards.size) return idx
        val landscapeKeyboard = availableKeyboards[idx].landscapeKeyboard
        return if (landscapeKeyboard.isNullOrBlank()) {
            idx
        } else {
            availableKeyboardIds.indexOf(landscapeKeyboard)
        }
    }

    /**
     * Change current display width when e.g. rotate the screen.
     */
    fun resize(displayWidth: Int) {
        if (currentId >= 0 && (displayWidth == currentDisplayWidth)) return

        currentDisplayWidth = displayWidth
        newOrReset()
    }
}
