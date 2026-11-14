package com.klisee.reloaded

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.klisee.reloaded.gmk.R

internal class LoadingOverlay(context: Context) {
    private val dialog = Dialog(context, R.style.Theme_Reloaded_Loading)
    private val content = LoadingOverlayView(context)
    private var closing = false
    private var destroyed = false

    init {
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setContentView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                hideSystemBars(window)
            }
        }
        dialog.setOnDismissListener {
            destroyed = true
        }
        dialog.window?.let { window ->
            window.setGravity(Gravity.FILL)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            hideSystemBars(window)
        }
        content.alpha = 0f
    }

    fun show() {
        if (closing || destroyed || dialog.isShowing) return
        runCatching { dialog.show() }
        content.requestFocus()
        content.animate().alpha(1f).setDuration(220L).start()
    }

    fun dismiss() {
        if (closing) return
        closing = true
        content.animate().cancel()
        if (!dialog.isShowing) {
            destroyed = true
            return
        }
        content.animate()
            .alpha(0f)
            .setDuration(170L)
            .withEndAction {
                runCatching { dialog.dismiss() }
                destroyed = true
            }
            .start()
    }

    fun destroy() {
        closing = true
        destroyed = true
        content.animate().cancel()
        runCatching { if (dialog.isShowing) dialog.dismiss() }
    }

    private fun hideSystemBars(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
