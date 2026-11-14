package com.klisee.reloaded

internal class LoadingOverlayGate {
    private var showing = false
    private var finished = false

    val isShowing: Boolean
        get() = showing

    fun show(): Boolean {
        if (finished || showing) return false
        showing = true
        return true
    }

    fun finish(): Boolean {
        if (finished) return false
        finished = true
        showing = false
        return true
    }

    fun destroy(): Boolean {
        val wasShowing = showing
        finished = true
        showing = false
        return wasShowing
    }

    companion object {
        const val MAX_WAIT_MILLIS = 30_000L
    }
}
