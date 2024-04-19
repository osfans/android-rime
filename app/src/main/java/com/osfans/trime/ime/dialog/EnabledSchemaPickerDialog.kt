package com.osfans.trime.ime.dialog

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager

object EnabledSchemaPickerDialog {
    suspend fun build(
        rime: RimeApi,
        scope: LifecycleCoroutineScope,
        context: Context,
        extensions: (AlertDialog.Builder.() -> AlertDialog.Builder)? = null,
    ): AlertDialog {
        val selecteds = rime.selectedSchemata()
        val selectedNames = selecteds.mapNotNull { it.name }
        val selectedIds = selecteds.mapNotNull { it.schemaId }
        val selectedSchemaId = rime.selectedSchemaId()
        val selectedIndex = selecteds.indexOfFirst { it.schemaId == selectedSchemaId }
        return AlertDialog.Builder(context).apply {
            setTitle(R.string.select_current_schema)
            if (rime.isEmpty()) {
                setMessage(R.string.no_schema_to_select)
            } else {
                setSingleChoiceItems(
                    selectedNames.toTypedArray(),
                    selectedIndex,
                ) { dialog, which ->
                    scope.launch {
                        rime.selectSchema(selectedIds[which])
                        dialog.dismiss()
                    }
                }
            }
            setNeutralButton(R.string.other_ime) { _, _ ->
                inputMethodManager.showInputMethodPicker()
            }
            extensions?.invoke(this)
        }.create()
    }
}
