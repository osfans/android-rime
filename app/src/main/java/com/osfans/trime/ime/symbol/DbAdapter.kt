// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.launch

class DbAdapter(
    private val ctx: Context,
    private val service: TrimeInputMethodService,
    theme: Theme,
) : FlexibleAdapter(theme) {
    var type = SymbolBoardType.CLIPBOARD

    override fun onPaste(bean: DatabaseBean) {
        service.commitText(bean.text ?: "")
    }

    override fun onPin(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                SymbolBoardType.CLIPBOARD -> ClipboardHelper.pin(bean.id)
                SymbolBoardType.COLLECTION -> CollectionHelper.pin(bean.id)
                SymbolBoardType.DRAFT -> DraftHelper.pin(bean.id)
                else -> {}
            }
            refresh()
        }
    }

    override fun onUnpin(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                SymbolBoardType.CLIPBOARD -> ClipboardHelper.unpin(bean.id)
                SymbolBoardType.COLLECTION -> CollectionHelper.unpin(bean.id)
                SymbolBoardType.DRAFT -> DraftHelper.unpin(bean.id)
                else -> {}
            }
            refresh()
        }
    }

    override fun onDelete(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                SymbolBoardType.CLIPBOARD -> ClipboardHelper.delete(bean.id)
                SymbolBoardType.COLLECTION -> CollectionHelper.delete(bean.id)
                SymbolBoardType.DRAFT -> DraftHelper.delete(bean.id)
                else -> {}
            }
            refresh()
        }
    }

    override fun onEdit(bean: DatabaseBean) {
        bean.text?.let { AppUtils.launchLiquidKeyboardEdit(ctx, type, bean.id, it) }
    }

    override fun onCollect(bean: DatabaseBean) {
        service.lifecycleScope.launch { CollectionHelper.insert(DatabaseBean(text = bean.text)) }
    }

    // FIXME: 这个方法可能实现得比较粗糙，需要日后改进
    override fun onDeleteAll() {
        val confirm =
            AlertDialog
                .Builder(context)
                .setTitle(R.string.delete_all)
                .setMessage(R.string.liquid_keyboard_ask_to_delete_all)
                .setPositiveButton(R.string.ok) { _, _ ->
                    service.lifecycleScope.launch {
                        when (type) {
                            SymbolBoardType.CLIPBOARD -> ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned())
                            SymbolBoardType.COLLECTION -> CollectionHelper.deleteAll(CollectionHelper.haveUnpinned())
                            SymbolBoardType.DRAFT -> DraftHelper.deleteAll(DraftHelper.haveUnpinned())
                            else -> {}
                        }
                        refresh()
                    }
                }.setNegativeButton(R.string.cancel, null)
                .create()
        service.showDialog(confirm)
    }

    override val showCollectButton: Boolean = type != SymbolBoardType.COLLECTION

    private suspend fun refresh() {
        when (type) {
            SymbolBoardType.CLIPBOARD -> submitList(ClipboardHelper.getAll())
            SymbolBoardType.COLLECTION -> submitList(CollectionHelper.getAll())
            SymbolBoardType.DRAFT -> submitList(DraftHelper.getAll())
            else -> {}
        }
    }
}
