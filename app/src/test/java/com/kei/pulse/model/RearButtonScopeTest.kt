package com.kei.pulse.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearButtonScopeTest {

    @Test
    fun `disabled never filters, regardless of mode, allowlist or foreground app`() {
        assertFalse(RearButtonScope.shouldFilter(enabled = false, mode = RearButtonScopeMode.EVERYWHERE, scopedPackages = emptySet(), foregroundPackage = null))
        assertFalse(RearButtonScope.shouldFilter(enabled = false, mode = RearButtonScopeMode.SELECTED_APPS, scopedPackages = setOf("com.example.game"), foregroundPackage = "com.example.game"))
    }

    @Test
    fun `everywhere mode filters regardless of allowlist or foreground app`() {
        assertTrue(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.EVERYWHERE, scopedPackages = emptySet(), foregroundPackage = null))
        assertTrue(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.EVERYWHERE, scopedPackages = emptySet(), foregroundPackage = "com.example.anything"))
        // A remembered allowlist is inert while the mode is EVERYWHERE.
        assertTrue(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.EVERYWHERE, scopedPackages = setOf("com.example.game"), foregroundPackage = "com.example.other"))
    }

    @Test
    fun `selected-apps mode only filters while a listed app is foreground`() {
        val scoped = setOf("com.example.game")
        assertTrue(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.SELECTED_APPS, scopedPackages = scoped, foregroundPackage = "com.example.game"))
        assertFalse(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.SELECTED_APPS, scopedPackages = scoped, foregroundPackage = "com.example.other"))
    }

    @Test
    fun `selected-apps mode with an empty allowlist fails closed, not open`() {
        // The mode chip says "Selected apps" — an empty list must not silently behave like Everywhere.
        assertFalse(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.SELECTED_APPS, scopedPackages = emptySet(), foregroundPackage = "com.example.anything"))
    }

    @Test
    fun `selected-apps mode with unknown foreground fails closed`() {
        // Bootstrap gap: right after the service (re)connects, before the first window-state event.
        assertFalse(RearButtonScope.shouldFilter(enabled = true, mode = RearButtonScopeMode.SELECTED_APPS, scopedPackages = setOf("com.example.game"), foregroundPackage = null))
    }

    @Test
    fun `system UI windows are recognized as transient, not a real foreground-app change`() {
        // On-device: the volume panel our own volume actions trigger is one of these — without this check,
        // showing it reads as "left the scoped game" and the next press goes unfiltered.
        assertTrue(RearButtonScope.isTransientSystemWindow("com.android.systemui"))
        assertTrue(RearButtonScope.isTransientSystemWindow("android"))
        assertFalse(RearButtonScope.isTransientSystemWindow("com.example.game"))
    }
}
