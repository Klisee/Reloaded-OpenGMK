package com.klisee.reloaded.gmk.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignersMatchTest {
    private val oldKey = "aaa111"
    private val newKey = "bbb222"
    private val foreign = "ccc333"

    @Test
    fun acepta_la_misma_clave() {
        assertTrue(signersMatch(setOf(oldKey), emptySet(), setOf(oldKey), emptySet()))
    }

    @Test
    fun rechaza_una_clave_ajena() {
        assertFalse(signersMatch(setOf(oldKey), emptySet(), setOf(foreign), emptySet()))
    }

    @Test
    fun rechaza_un_apk_sin_firma() {
        assertFalse(signersMatch(setOf(oldKey), emptySet(), emptySet(), emptySet()))
        assertFalse(signersMatch(emptySet(), emptySet(), setOf(oldKey), emptySet()))
    }

    @Test
    fun acepta_una_rotacion_de_clave_aprobada_por_el_historial() {
        // El instalado sigue con la clave vieja, pero el APK nuevo trae la nueva y la vieja en el historial.
        assertTrue(signersMatch(setOf(oldKey), emptySet(), setOf(newKey), setOf(oldKey)))
        // Y al revés: el instalado ya rotó y el APK viene con la clave anterior.
        assertTrue(signersMatch(setOf(newKey), setOf(oldKey), setOf(oldKey), emptySet()))
    }

    @Test
    fun una_clave_que_no_esta_en_ningun_historial_se_rechaza() {
        assertFalse(signersMatch(setOf(oldKey), setOf(newKey), setOf(foreign), setOf(foreign)))
    }
}
