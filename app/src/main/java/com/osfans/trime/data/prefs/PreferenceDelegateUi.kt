/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference

abstract class PreferenceDelegateUi<T : Preference>(
    val key: String,
    private val enableUiOn: (() -> Boolean)? = null,
) {
    abstract fun createUi(context: Context): T

    fun isEnabled() = enableUiOn?.invoke() ?: true

    class StringList<T : Any>(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: T,
        val serializer: PreferenceDelegate.Serializer<T>,
        val entryValues: List<T>,
        @StringRes
        val entryLabels: List<Int>,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi<ListPreference>(key, enableUiOn) {
        override fun createUi(context: Context) =
            ListPreference(context).apply {
                key = this@StringList.key
                isIconSpaceReserved = false
                isSingleLineTitle = false
                entryValues = this@StringList.entryValues.map { serializer.serialize(it) }.toTypedArray()
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setDefaultValue(serializer.serialize(defaultValue))
                setTitle(this@StringList.title)
                entries = this@StringList.entryLabels.map { context.getString(it) }.toTypedArray()
                setDialogTitle(this@StringList.title)
            }
    }
}
