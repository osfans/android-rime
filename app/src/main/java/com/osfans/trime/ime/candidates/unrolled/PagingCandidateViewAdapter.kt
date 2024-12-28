/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder

open class PagingCandidateViewAdapter(
    val theme: Theme,
) : PagingDataAdapter<CandidateItem, CandidateViewHolder>(diffCallback) {
    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<CandidateItem>() {
                override fun areItemsTheSame(
                    oldItem: CandidateItem,
                    newItem: CandidateItem,
                ): Boolean = oldItem === newItem

                override fun areContentsTheSame(
                    oldItem: CandidateItem,
                    newItem: CandidateItem,
                ): Boolean = oldItem == newItem
            }
    }

    var offset: Int = 0
        private set

    fun refreshWithOffset(offset: Int) {
        this.offset = offset
        refresh()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder = CandidateViewHolder(CandidateItemUi(parent.context, theme))

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
    ) {
        val item = getItem(position) ?: return
        val obtainComment = snapshot().items.any { it.comment.isNotEmpty() }
        holder.ui.update(item, false, obtainComment)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = position + offset
    }
}
