package com.kei.pulse.model

/** What [RearButtonMapper] decides to do with one M1/M2 key event. */
sealed class RearButtonOutcome {
    /** Not ours (feature off, unmapped keycode, or mapped to [RearButtonAction.NONE]) — let the event through. */
    object PassThrough : RearButtonOutcome()

    /**
     * Swallow the event so the foreground app never sees it. [action] is the action to fire now, or null on
     * the matching UP (the DOWN already fired it; the UP is consumed but does nothing).
     */
    data class Consume(val action: RearButtonAction?) : RearButtonOutcome()
}

/**
 * Pure decision core for Odin rear-button mapping — no Android imports, so it's unit-testable with zero
 * framework. [com.kei.pulse.input.OdinButtonService] calls [map] on every key event and acts on the result.
 */
object RearButtonMapper {

    /** Fires on hold for these actions only — a one-shot volume nudge per press would be unusable. */
    fun isRepeatable(action: RearButtonAction): Boolean =
        action == RearButtonAction.VOLUME_UP || action == RearButtonAction.VOLUME_DOWN

    /** Delay before the first repeat, and the interval between repeats after that, while an action is held. */
    const val REPEAT_INITIAL_DELAY_MS = 400L
    const val REPEAT_INTERVAL_MS = 120L

    fun map(enabled: Boolean, m1: RearButtonAction, m2: RearButtonAction, keyCode: Int, down: Boolean): RearButtonOutcome {
        val button = RearButtons.forKeyCode(keyCode) ?: return RearButtonOutcome.PassThrough
        if (!enabled) return RearButtonOutcome.PassThrough
        val action = if (button == RearButtons.M1) m1 else m2
        if (action == RearButtonAction.NONE) return RearButtonOutcome.PassThrough
        return RearButtonOutcome.Consume(if (down) action else null)
    }
}
