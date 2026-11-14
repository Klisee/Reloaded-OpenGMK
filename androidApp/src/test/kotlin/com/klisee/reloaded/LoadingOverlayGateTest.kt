package com.klisee.reloaded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingOverlayGateTest {
    @Test
    fun showsOnceUntilReady() {
        val gate = LoadingOverlayGate()

        assertTrue(gate.show())
        assertTrue(gate.isShowing)
        assertFalse(gate.show())
    }

    @Test
    fun readyBeforeShowSuppressesOverlay() {
        val gate = LoadingOverlayGate()

        assertTrue(gate.finish())
        assertFalse(gate.show())
        assertFalse(gate.isShowing)
    }

    @Test
    fun readyWhileShowingDismissesOnce() {
        val gate = LoadingOverlayGate()

        assertTrue(gate.show())
        assertTrue(gate.finish())
        assertFalse(gate.isShowing)
        assertFalse(gate.finish())
    }

    @Test
    fun destroyPreventsShowAndReportsVisibility() {
        val gate = LoadingOverlayGate()

        assertTrue(gate.show())
        assertTrue(gate.destroy())
        assertFalse(gate.isShowing)
        assertFalse(gate.show())
        assertFalse(gate.finish())
    }

    @Test
    fun timeoutIsCappedAtThirtySeconds() {
        assertEquals(30_000L, LoadingOverlayGate.MAX_WAIT_MILLIS)
    }

    @Test
    fun loadingLabelMatchesLauncherCopy() {
        assertEquals("Cargando...", LoadingOverlayView.LOADING_LABEL)
    }
}
