// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

open class CompactCandidateViewAdapter(
    val theme: Theme,
) : BaseQuickAdapter<CandidateItem, CandidateViewHolder>() {
    var isLastPage: Boolean = false
        private set

    var previous: Int = 0
        private set

    var highlightedIdx: Int = -1
        private set

    fun updateCandidates(
        list: List<CandidateItem>,
        isLastPage: Boolean,
        previous: Int,
        highlightedIdx: Int,
    ) {
        this.isLastPage = isLastPage
        this.previous = previous
        this.highlightedIdx = highlightedIdx
        super.submitList(list)
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        val ui = CandidateItemUi(context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
        item: CandidateItem?,
    ) {
        item ?: return
        val isHighlighted = theme.generalStyle.candidateUseCursor && position == highlightedIdx
        val obtainComment = items.any { it.comment.isNotEmpty() }
        holder.ui.update(item, isHighlighted, obtainComment)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = previous + position // unused
        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
            minWidth = 0
            flexGrow = 0f
        }
    }
}
