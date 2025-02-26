package com.lagradost.shiro.ui

object Stopwatch {
    inline fun elapse(callback: () -> Unit): Long {
        var start = System.currentTimeMillis()
        callback()
        return System.currentTimeMillis() - start
    }

    inline fun elapseNano(callback: () -> Unit): Long {
        var start = System.nanoTime()
        callback()
        return System.nanoTime() - start
    }
}