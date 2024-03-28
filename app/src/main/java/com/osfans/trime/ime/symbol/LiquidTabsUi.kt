package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.PaintDrawable
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.text.ScrollView
import com.osfans.trime.util.rippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.gravityCenterVertical
import splitties.views.horizontalPadding

class LiquidTabsUi(override val ctx: Context, val theme: Theme) : Ui {
    inner class TabUi : Ui {
        override val ctx = this@LiquidTabsUi.ctx

        var position: Int = -1

        val text =
            textView {
                textSize = theme.style.getFloat("candidate_text_size")
                typeface = FontManager.getTypeface("candidate_font")
                ColorManager.getColor("candidate_text_color")?.let { setTextColor(it) }
            }

        override val root =
            frameLayout {
                add(
                    text,
                    lParams {
                        gravity = gravityCenter
                        horizontalPadding = dp(theme.style.getFloat("candidate_padding")).toInt()
                    },
                )
                background = rippleDrawable(ColorManager.getColor("hilited_candidate_back_color")!!)
                setOnClickListener {
                    onTabClick(this@TabUi)
                }
            }

        fun setText(str: String) {
            text.text = str
        }

        fun setActive(active: Boolean) {
            val color =
                if (active) {
                    ColorManager.getColor(
                        "hilited_candidate_text_color",
                    )!!
                } else {
                    ColorManager.getColor("candidate_text_color")!!
                }
            val background = if (active) ColorManager.getColor("hilited_candidate_back_color")!! else Color.TRANSPARENT
            text.setTextColor(color)
            root.background = PaintDrawable(background).apply { setCornerRadius(theme.style.getFloat("layout/round_corner")) }
        }
    }

    private var tabs: Array<TabUi> = arrayOf()
    private var selected = -1

    private var onTabClick: (TabUi.(Int) -> Unit)? = null

    private val horizontal = horizontalLayout()

    override val root =
        ScrollView(ctx, null).apply {
            isHorizontalScrollBarEnabled = false
            add(
                horizontal,
                lParams(wrapContent, matchParent) {
                    gravity = gravityCenterVertical
                },
            )
            post {
                scrollX = tabs[selected].root.left
            }
        }

    fun setTabs(tags: List<TabTag>) {
        tabs.forEach { root.removeView(it.root) }
        selected = -1
        tabs =
            Array(tags.size) {
                val tag = tags[it]
                TabUi().apply {
                    position = it
                    setText(tag.text)
                    setActive(false)
                }
            }
        tabs.forEach { tabUi ->
            horizontal.apply {
                add(
                    tabUi.root,
                    lParams(wrapContent, matchParent) {
                        gravity = gravityCenter
                    },
                )
            }
        }
    }

    fun activateTab(index: Int) {
        if (index == selected) return
        if (selected >= 0) {
            tabs[selected].setActive(false)
        }
        tabs[index].setActive(true)
        selected = index
    }

    private fun onTabClick(tabUi: TabUi) {
        onTabClick?.invoke(tabUi, tabUi.position)
    }

    fun setOnTabClickListener(listener: (TabUi.(Int) -> Unit)? = null) {
        onTabClick = listener
    }
}
