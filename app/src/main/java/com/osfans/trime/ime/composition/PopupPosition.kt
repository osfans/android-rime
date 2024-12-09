/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class PopupPosition(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    // 相对位置
    BOTTOM_LEFT(R.string.bottom_left), // 默认
    BOTTOM_RIGHT(R.string.bottom_right),
    TOP_LEFT(R.string.top_left),
    TOP_RIGHT(R.string.top_right),

    // 跟随光标
    FOLLOW(R.string.follow_cursor),
}
