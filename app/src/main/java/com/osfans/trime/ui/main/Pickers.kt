package com.osfans.trime.ui.main

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.core.SchemaListItem
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.sound.SoundEffect
import com.osfans.trime.data.sound.SoundEffectManager
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.RimeWrapper
import com.osfans.trime.ui.components.CoroutineChoiceDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun Context.themePicker(
    @StyleRes themeResId: Int = 0,
): AlertDialog {
    return CoroutineChoiceDialog(this, themeResId).apply {
        title = getString(R.string.looks__selected_theme_title)
        initDispatcher = Dispatchers.IO
        onInit {
            items =
                ThemeManager.getAllThemes()
                    .map { it.substringBeforeLast('.') }
                    .toTypedArray()
            val current =
                AppPrefs.defaultInstance().theme.selectedTheme
                    .substringBeforeLast('.')
            checkedItem = items.indexOf(current)
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            with(items[checkedItem].toString()) {
                ThemeManager.setNormalTheme(if (this == "trime") this else "$this.trime")
            }
        }
    }.create()
}

suspend fun Context.colorPicker(
    @StyleRes themeResId: Int = 0,
): AlertDialog {
    return CoroutineChoiceDialog(this, themeResId).apply {
        title = getString(R.string.looks__selected_color_title)
        initDispatcher = Dispatchers.Default
        val all by lazy { ColorManager.presetColorSchemes }
        onInit {
            items = all.map { it.value["name"]!! }.toTypedArray()
            val current = ColorManager.selectedColor
            val schemeIds = all.keys
            checkedItem = schemeIds.indexOf(current).takeIf { it > -1 } ?: 1
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            val schemeIds = all.keys.toList()
            ColorManager.setColorScheme(schemeIds[checkedItem])
        }
    }.create()
}

fun Context.schemaPicker(
    @StyleRes themeResId: Int = 0,
): AlertDialog {
    val available = Rime.getAvailableRimeSchemaList()
    val selected = Rime.getSelectedRimeSchemaList()
    val availableIds = available.mapNotNull(SchemaListItem::schemaId)
    val selectedIds = selected.mapNotNull(SchemaListItem::schemaId)
    val checked = availableIds.map(selectedIds::contains).toBooleanArray()
    return AlertDialog.Builder(this, themeResId)
        .setTitle(R.string.pref_select_schemas)
        .setMultiChoiceItems(
            available.mapNotNull(SchemaListItem::name).toTypedArray(),
            checked,
        ) { _, id, isChecked -> checked[id] = isChecked }
        .setPositiveButton(android.R.string.ok) { _, _ ->
            (this as LifecycleOwner).lifecycleScope.launch {
                Rime.selectRimeSchemas(
                    availableIds
                        .filterIndexed { i, _ -> checked[i] }
                        .toTypedArray(),
                )
                withContext(Dispatchers.Default) {
                    RimeWrapper.deploy()
                }
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
}

fun Context.soundPicker(
    @StyleRes themeResId: Int = 0,
): AlertDialog {
    val all = SoundEffectManager.getAllSoundEffects().mapNotNull(SoundEffect::name)
    val current = SoundEffectManager.getActiveSoundEffect().getOrNull()?.name ?: ""
    var checked = all.indexOf(current)
    return AlertDialog.Builder(this, themeResId)
        .setTitle(R.string.keyboard__key_sound_package_title)
        .setSingleChoiceItems(
            all.toTypedArray(),
            checked,
        ) { _, id -> checked = id }
        .setPositiveButton(android.R.string.ok) { _, _ ->
            SoundEffectManager.switchSound(all[checked])
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
}
