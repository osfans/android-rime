// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** 按键行为枚举  */
enum class KeyBehavior {
    // 长按按键展开列表时，正上方为长按对应按键，排序如上，不展示combo及之前的按键，展示extra
    COMPOSING,
    HAS_MENU,
    PAGING,
    COMBO,
    ASCII,
    CLICK,
    SWIPE_UP,
    LONG_CLICK,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    EXTRA,
}
