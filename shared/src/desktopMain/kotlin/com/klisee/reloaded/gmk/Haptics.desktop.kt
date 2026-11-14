package com.klisee.reloaded.gmk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberInterfaceHaptics(): InterfaceHaptics = remember {
    InterfaceHaptics(onTick = {}, onConfirm = {})
}
