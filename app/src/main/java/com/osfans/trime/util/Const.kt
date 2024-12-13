// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import com.osfans.trime.BuildConfig

object Const {
    const val BUILDER = BuildConfig.BUILDER
    const val BUILD_TIMESTAMP = BuildConfig.BUILD_TIMESTAMP
    const val BUILD_COMMIT_HASH = BuildConfig.BUILD_COMMIT_HASH
    const val VERSION_NAME = "${BuildConfig.BUILD_VERSION_NAME}-${BuildConfig.BUILD_TYPE}"
    const val GIT_REPO = BuildConfig.BUILD_GIT_REPO

    const val LIBRIME_VERSION = BuildConfig.LIBRIME_VERSION
    const val OPENCC_VERSION = BuildConfig.OPENCC_VERSION
}
