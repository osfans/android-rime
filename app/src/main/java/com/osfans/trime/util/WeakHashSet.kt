// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import java.util.Collections
import java.util.WeakHashMap

@Suppress("FunctionName")
fun <T> WeakHashSet(): MutableSet<T> = Collections.newSetFromMap(WeakHashMap<T, Boolean>())
