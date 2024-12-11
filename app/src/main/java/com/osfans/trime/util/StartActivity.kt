/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment

inline fun <reified T : Activity> Context.startActivity(setupIntent: Intent.() -> Unit = {}) {
    startActivity(Intent(this, T::class.java).apply(setupIntent))
}

inline fun <reified T : Activity> Fragment.startActivity(setupIntent: Intent.() -> Unit = {}) {
    requireContext().startActivity<T>(setupIntent)
}
