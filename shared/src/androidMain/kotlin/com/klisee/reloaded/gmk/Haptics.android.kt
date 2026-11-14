package com.klisee.reloaded.gmk

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberInterfaceHaptics(): InterfaceHaptics {
    val view = LocalView.current
    return remember(view) {
        InterfaceHaptics(
            onTick = {
                if (!view.isHapticFeedbackEnabled) return@InterfaceHaptics
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CLOCK_TICK
                    } else {
                        HapticFeedbackConstants.VIRTUAL_KEY
                    },
                )
            },
            onConfirm = {
                if (!view.isHapticFeedbackEnabled) return@InterfaceHaptics
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.VIRTUAL_KEY
                    },
                )
            },
        )
    }
}
