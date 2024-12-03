/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.UnderlineSpan
import androidx.annotation.ColorInt
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.osfans.trime.core.RimeProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView
import splitties.views.horizontalPadding

class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
) : Ui {
    class CandidateSpan(
        @ColorInt private val color: Int,
        private val textSize: Float,
        private val typeface: Typeface,
    ) : UnderlineSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            ds.color = color
            ds.textSize = textSize
            ds.typeface = typeface
        }
    }

    private val labelSize = theme.generalStyle.labelTextSize
    private val textSize = theme.generalStyle.candidateTextSize
    private val commentSize = theme.generalStyle.commentTextSize
    private val labelFont = FontManager.getTypeface("label_font")
    private val textFont = FontManager.getTypeface("candidate_font")
    private val commentFont = FontManager.getTypeface("comment_font")
    private val labelColor = ColorManager.getColor("label_color")!!
    private val textColor = ColorManager.getColor("candidate_text_color")!!
    private val commentColor = ColorManager.getColor("comment_text_color")!!
    private val highlightLabelColor = ColorManager.getColor("hilited_label_color")!!
    private val highlightCommentTextColor = ColorManager.getColor("hilited_comment_text_color")!!
    private val highlightCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")!!
    private val highlightBackColor = ColorManager.getColor("hilited_back_color")!!

    override val root =
        textView {
            horizontalPadding = dp(theme.generalStyle.candidatePadding)
        }

    fun update(
        candidate: RimeProto.Candidate,
        highlighted: Boolean,
    ) {
        val labelFg = if (highlighted) highlightLabelColor else labelColor
        val textFg = if (highlighted) highlightCandidateTextColor else textColor
        val commentFg = if (highlighted) highlightCommentTextColor else commentColor
        root.text =
            buildSpannedString {
                inSpans(CandidateSpan(labelFg, ctx.sp(labelSize), labelFont)) { append(candidate.label) }
                inSpans(CandidateSpan(textFg, ctx.sp(textSize), textFont)) { append(candidate.text) }
                if (!candidate.comment.isNullOrEmpty()) {
                    append(" ")
                    inSpans(CandidateSpan(commentFg, ctx.sp(commentSize), commentFont)) { append(candidate.comment) }
                }
            }
        val bg = if (highlighted) highlightBackColor else Color.TRANSPARENT
        root.backgroundColor = bg
    }
}
