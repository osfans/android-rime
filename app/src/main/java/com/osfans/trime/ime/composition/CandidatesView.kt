/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.popup.PagedCandidatesUi
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding
import splitties.views.verticalPadding

@SuppressLint("ViewConstructor")
class CandidatesView(
    val ctx: Context,
    val rime: RimeSession,
    val theme: Theme,
) : ConstraintLayout(ctx) {
    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private var menu = RimeProto.Context.Menu()
    private var inputComposition = RimeProto.Context.Composition()

    private val preeditUi =
        PreeditUi(ctx, theme).apply {
            preedit.setOnCursorMoveListener { position ->
                rime.launchOnReady { it.moveCursorPos(position) }
            }
        }

    private val candidatesUi =
        PagedCandidatesUi(ctx, theme).apply {
            candidatesAdapter.setOnItemClickListener { _, _, position ->
                rime.launchOnReady { it.selectPagedCandidate(position) }
            }
        }

    fun update(ctx: RimeProto.Context) {
        inputComposition = ctx.composition
        menu = ctx.menu
        updateUi()
    }

    private fun evaluateVisibility(): Boolean =
        !inputComposition.preedit.isNullOrEmpty() ||
            menu.candidates.isNotEmpty()

    private fun updateUi() {
        if (evaluateVisibility()) {
            preeditUi.update(inputComposition)
            // if CandidatesView can be shown, rime engine is ready most of the time,
            // so it should be safety to get option immediately
            val isHorizontalLayout = rime.run { getRuntimeOption("_horizontal") }
            when (candidatesMode) {
                PopupCandidatesMode.CURRENT_PAGE -> {
                    candidatesUi.root.let {
                        if (it.visibility == View.GONE) {
                            it.visibility = View.VISIBLE
                        }
                    }
                    candidatesUi.update(menu, isHorizontalLayout)
                }

                PopupCandidatesMode.PREEDIT_ONLY -> {
                    candidatesUi.root.let {
                        if (it.visibility != View.GONE) {
                            it.visibility = View.GONE
                            candidatesUi.update(RimeProto.Context.Menu(), isHorizontalLayout)
                        }
                    }
                }
            }
        }
    }

    init {
        verticalPadding = dp(theme.generalStyle.layout.marginX)
        horizontalPadding = dp(theme.generalStyle.layout.marginY)
        add(
            preeditUi.root,
            lParams(wrapContent, wrapContent) {
                topOfParent()
                startOfParent()
            },
        )
        add(
            candidatesUi.root,
            lParams(wrapContent, wrapContent) {
                below(preeditUi.root)
                startOfParent()
                bottomOfParent()
            },
        )

        isFocusable = false
        layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
    }
}
