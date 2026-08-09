package com.kei.pulse.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearButtonMapperTest {

    @Test
    fun `disabled always passes through, even for a mapped button`() {
        val outcome = RearButtonMapper.map(
            enabled = false, m1 = RearButtonAction.VOLUME_DOWN, m2 = RearButtonAction.VOLUME_UP,
            keyCode = RearButtons.M1.keyCode, down = true,
        )
        assertEquals(RearButtonOutcome.PassThrough, outcome)
    }

    @Test
    fun `unmapped keycode passes through regardless of enabled state`() {
        val outcome = RearButtonMapper.map(
            enabled = true, m1 = RearButtonAction.VOLUME_DOWN, m2 = RearButtonAction.VOLUME_UP,
            keyCode = 99, down = true,
        )
        assertEquals(RearButtonOutcome.PassThrough, outcome)
    }

    @Test
    fun `action NONE passes the button through untouched`() {
        val outcome = RearButtonMapper.map(
            enabled = true, m1 = RearButtonAction.NONE, m2 = RearButtonAction.VOLUME_UP,
            keyCode = RearButtons.M1.keyCode, down = true,
        )
        assertEquals(RearButtonOutcome.PassThrough, outcome)
    }

    @Test
    fun `mapped DOWN fires the action, matching UP is consumed but fires nothing`() {
        val down = RearButtonMapper.map(
            enabled = true, m1 = RearButtonAction.VOLUME_DOWN, m2 = RearButtonAction.VOLUME_UP,
            keyCode = RearButtons.M1.keyCode, down = true,
        )
        assertEquals(RearButtonOutcome.Consume(RearButtonAction.VOLUME_DOWN), down)

        val up = RearButtonMapper.map(
            enabled = true, m1 = RearButtonAction.VOLUME_DOWN, m2 = RearButtonAction.VOLUME_UP,
            keyCode = RearButtons.M1.keyCode, down = false,
        )
        assertEquals(RearButtonOutcome.Consume(null), up)
    }

    @Test
    fun `M1 and M2 resolve independently to their own configured action`() {
        val m1 = RearButtonMapper.map(
            enabled = true, m1 = RearButtonAction.BACK, m2 = RearButtonAction.HOME,
            keyCode = RearButtons.M1.keyCode, down = true,
        )
        val m2 = RearButtonMapper.map(
            enabled = true, m1 = RearButtonAction.BACK, m2 = RearButtonAction.HOME,
            keyCode = RearButtons.M2.keyCode, down = true,
        )
        assertEquals(RearButtonOutcome.Consume(RearButtonAction.BACK), m1)
        assertEquals(RearButtonOutcome.Consume(RearButtonAction.HOME), m2)
    }

    @Test
    fun `only volume actions repeat on hold`() {
        assertTrue(RearButtonMapper.isRepeatable(RearButtonAction.VOLUME_UP))
        assertTrue(RearButtonMapper.isRepeatable(RearButtonAction.VOLUME_DOWN))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.MEDIA_NEXT))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.BACK))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.HOME))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.RECENTS))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.MEDIA_PLAY_PAUSE))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.MEDIA_PREVIOUS))
        assertFalse(RearButtonMapper.isRepeatable(RearButtonAction.NONE))
    }
}
