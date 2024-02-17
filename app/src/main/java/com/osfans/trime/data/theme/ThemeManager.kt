package com.osfans.trime.data.theme

import androidx.annotation.Keep
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.ime.symbol.TabManager
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    /**
     * Update sharedThemes and userThemes.
     */
    @Keep
    private val onDataDirChange =
        DataManager.OnDataDirChangeListener {
            refreshThemes()
        }

    init {
        // register listener
        DataManager.addOnChangedListener(onDataDirChange)
    }

    private fun listThemes(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    private val sharedThemes: MutableList<String> = listThemes(DataManager.sharedDataDir)

    private val userThemes: MutableList<String> = listThemes(DataManager.userDataDir)

    fun getAllThemes(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userThemes
        }
        return sharedThemes + userThemes
    }

    private fun refreshThemes() {
        sharedThemes.clear()
        userThemes.clear()
        sharedThemes.addAll(listThemes(DataManager.sharedDataDir))
        userThemes.addAll(listThemes(DataManager.userDataDir))
    }

    val prefs = AppPrefs.defaultInstance().theme

    // 在初始化 ColorManager 时会被赋值
    lateinit var activeTheme: Theme
        private set

    fun init() = setNormalTheme()

    fun setNormalTheme(name: String = "") {
        if (name.isNotEmpty()) prefs.selectedTheme = name
        Theme().let {
            if (::activeTheme.isInitialized) {
                if (it == activeTheme) return
            }
            activeTheme = it
            // 由于这里的顺序不能打乱，不适合使用 listener
            EventManager.refresh()
            FontManager.refresh()
            ColorManager.refresh()
            TabManager.refresh()
        }
    }
}
