package com.osfans.trime.ime.broadcast

import com.osfans.trime.core.RimeNotification.OptionNotification
import com.osfans.trime.ime.window.BoardWindow

interface InputBroadcastReceiver {
    fun onRimeOptionUpdated(value: OptionNotification.Value) {}

    fun onWindowAttached(window: BoardWindow) {}

    fun onWindowDetached(window: BoardWindow) {}
}
