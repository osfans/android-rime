package com.osfans.trime.ime.dialog

import android.app.AlertDialog
import android.content.Context
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.daemon.RimeDaemon
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

object AvailableSchemaPickerDialog {
    suspend fun build(
        rime: RimeApi,
        context: Context,
    ): AlertDialog {
        val availables = rime.availableSchemata()
        val enableds = rime.enabledSchemata()
        val availableIds = availables.mapNotNull { it.schemaId }
        val enabledIds = enableds.mapNotNull { it.schemaId }
        val enabledBools = availableIds.map { enabledIds.contains(it) }.toBooleanArray()
        return AlertDialog.Builder(context).apply {
            setTitle(R.string.enable_schemata)
            if (availables.isEmpty()) {
                setMessage(R.string.no_schema_to_enable)
            } else {
                setMultiChoiceItems(availables.mapNotNull { it.name }.toTypedArray(), enabledBools) { _, which, isChecked ->
                    enabledBools[which] = isChecked
                }
                setPositiveButton(R.string.ok) { _, _ ->
                    val newEnabled = availableIds.filterIndexed { index, _ -> enabledBools[index] }
                    if (setOf(newEnabled) == setOf(enabledIds)) return@setPositiveButton
                    val scope = MainScope() + CoroutineName("AvailableSchemaPicker")
                    scope.launch {
                        rime.setEnabledSchemata(newEnabled.toTypedArray())
                        RimeDaemon.restartRime()
                    }
                }
            }
            setNegativeButton(R.string.cancel, null)
        }.create()
    }
}
