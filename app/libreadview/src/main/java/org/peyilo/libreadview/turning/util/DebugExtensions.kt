package org.peyilo.libreadview.turning.util

import org.peyilo.libreadview.util.LogHelper

internal fun computeTime(tag: String, msg: String, block: () -> Unit) {
    val start = System.nanoTime()
    block()
    val end = System.nanoTime()
    LogHelper.d(tag, "$msg: ${(end - start) / 1000}us")
}
