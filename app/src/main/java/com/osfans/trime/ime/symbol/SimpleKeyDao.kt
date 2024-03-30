package com.osfans.trime.ime.symbol

object SimpleKeyDao {
    fun simpleKeyboardData(raw: String): List<SimpleKeyBean> {
        val lines = raw.split("\n+".toRegex()).dropLastWhile { it.isEmpty() }
        return lines.filterNot { it.isEmpty() }.map { SimpleKeyBean(it) }
    }

    fun singleData(raw: String): List<SimpleKeyBean> {
        val list = mutableListOf<SimpleKeyBean>()
        var h = Char(0)
        for (element in raw) {
            if (element.isHighSurrogate()) {
                h = element
            } else if (element.isLowSurrogate()) {
                list.add(SimpleKeyBean(String(charArrayOf(h, element))))
            } else {
                list.add(SimpleKeyBean(element.toString()))
            }
        }
        return list
    }

    val operations =
        arrayOf(
            "liquid_keyboard_exit" to "返回",
            "space" to "空格",
            "BackSpace" to "退格",
            "Return" to "回车",
        )
}
