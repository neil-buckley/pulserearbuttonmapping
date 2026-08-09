package com.kei.pulse.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the M1/M2 → keycode table (see [RearButtons] KDoc for the hardware finding it's sourced from). */
class RearButtonsTest {

    @Test
    fun `M1 is keycode 98 (KEYCODE_BUTTON_C), M2 is keycode 101 (KEYCODE_BUTTON_Z)`() {
        assertEquals(98, RearButtons.M1.keyCode)
        assertEquals(101, RearButtons.M2.keyCode)
    }

    @Test
    fun `forKeyCode resolves known keycodes and rejects everything else`() {
        assertEquals(RearButtons.M1, RearButtons.forKeyCode(98))
        assertEquals(RearButtons.M2, RearButtons.forKeyCode(101))
        assertNull(RearButtons.forKeyCode(99))
        assertNull(RearButtons.forKeyCode(0))
    }
}
