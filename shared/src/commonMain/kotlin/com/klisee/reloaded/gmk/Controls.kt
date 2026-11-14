package com.klisee.reloaded.gmk

/**
 * Acciones del juego que se pueden reasignar a un botón del mando.
 * `defaultCode` es el código de botón Android (KeyEvent) con el que se juega por defecto.
 */
enum class ControlAction(val key: String, val defaultCode: Int) {
    Z("Z", 96),
    X("X", 97),
    C("C", 99),
    V("V", 100),
    ENTER("RETURN", 109),
    SPACE("SPACE", 108),
    B("B", 103),
    M("M", 102),
    O("O", 105),
    S("S", 104),
    E("E", 106),
}

/** Nombre del botón físico del mando para un código de Android. */
fun buttonName(code: Int): String = when (code) {
    96 -> "A"
    97 -> "B"
    99 -> "X"
    100 -> "Y"
    102 -> "L1"
    103 -> "R1"
    104 -> "L2"
    105 -> "R2"
    106 -> "Stick izquierdo"
    107 -> "Stick derecho"
    108 -> "Start"
    109 -> "Select"
    else -> "Botón $code"
}

/** Qué hace cada acción en el juego, para que la lista se entienda sin conocer el mando. */
val ControlAction.hint: String
    get() = when (this) {
        ControlAction.Z -> "Interactuar, hablar y confirmar"
        ControlAction.X -> "Cancelar y correr"
        ControlAction.C -> "Abrir el menú del juego"
        ControlAction.V -> "Acción secundaria"
        ControlAction.ENTER -> "Menú de pausa"
        ControlAction.SPACE -> "Avanzar texto"
        ControlAction.B -> "Atajo de combate"
        ControlAction.M -> "Abrir el mapa"
        ControlAction.O -> "Abrir la mochila"
        ControlAction.S -> "Usar el objeto registrado"
        ControlAction.E -> "Escapar de un Pokémon salvaje"
    }

/** Etiqueta corta para encabezados de grupo. */
val ControlAction.group: String
    get() = when (this) {
        ControlAction.Z, ControlAction.X -> "Juego"
        ControlAction.C, ControlAction.V, ControlAction.ENTER, ControlAction.SPACE -> "Menús"
        ControlAction.B, ControlAction.M, ControlAction.O, ControlAction.S, ControlAction.E -> "Atajos"
    }

/**
 * Código vk de la tecla con la que el juego espera esta acción. Es el valor de la derecha
 * en `controls.conf` (`origen=destino`): da igual si el origen es un botón de mando
 * (Android) o una tecla física (escritorio).
 */
val ControlAction.engineKeyCode: Int
    get() = when (this) {
        ControlAction.Z -> 90
        ControlAction.X -> 88
        ControlAction.C -> 67
        ControlAction.V -> 86
        ControlAction.ENTER -> 13
        ControlAction.SPACE -> 32
        ControlAction.B -> 66
        ControlAction.M -> 77
        ControlAction.O -> 79
        ControlAction.S -> 83
        ControlAction.E -> 69
    }

/**
 * Elige la tecla que activa esta acción.
 *
 * `controls.conf` guarda una línea por acción con el formato `origen=destino`:
 * - En Android, el origen es el código de botón del mando (96 = A, 97 = B, …).
 * - En escritorio, el origen es el código vk de la tecla física que se pulsa.
 * El destino siempre es la tecla que el juego lee (`engineKeyCode`).
 */
val ControlAction.defaultSourceKey: Int
    get() = engineKeyCode
