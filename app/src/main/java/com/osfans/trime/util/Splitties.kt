/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import splitties.experimental.InternalSplittiesApi
import splitties.resources.withResolvedThemeAttribute

@OptIn(InternalSplittiesApi::class)
fun Context.styledFloat(
    @AttrRes attrRes: Int,
) = withResolvedThemeAttribute(attrRes) {
    when (type) {
        TypedValue.TYPE_FLOAT -> float
        else -> throw IllegalArgumentException("float attribute expected")
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun View.styledFloat(
    @AttrRes attrRes: Int,
) = context.styledFloat(attrRes)

@Suppress("NOTHING_TO_INLINE")
inline fun Fragment.styledFloat(
    @AttrRes attrRes: Int,
) = context!!.styledFloat(attrRes)
