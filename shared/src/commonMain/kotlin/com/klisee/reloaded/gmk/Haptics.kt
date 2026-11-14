package com.klisee.reloaded.gmk

import androidx.compose.runtime.Composable

/**
 * Vibración corta al tocar la interfaz. En Android usa el motor de háptica del sistema
 * (respeta el ajuste de vibración del teléfono); en escritorio no hace nada.
 */
@Composable
expect fun rememberInterfaceHaptics(): InterfaceHaptics

class InterfaceHaptics internal constructor(
    private val onTick: () -> Unit,
    private val onConfirm: () -> Unit,
) {
    /** Toque ligero: cambiar de pestaña, abrir un menú. */
    fun tick() = onTick()

    /** Confirmación algo más marcada: acciones importantes. */
    fun confirm() = onConfirm()
}
