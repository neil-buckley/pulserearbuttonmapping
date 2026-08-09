package com.kei.pulse.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearButtonScopeTest {

    @Test
    fun `disabled never filters, regardless of allowlist or foreground app`() {
        assertFalse(RearButtonScope.shouldFilter(enabled = false, scopedPackages = emptySet(), foregroundPackage = null))
        assertFalse(RearButtonScope.shouldFilter(enabled = false, scopedPackages = setOf("com.example.game"), foregroundPackage = "com.example.game"))
    }

    @Test
    fun `enabled with empty allowlist filters everywhere`() {
        assertTrue(RearButtonScope.shouldFilter(enabled = true, scopedPackages = emptySet(), foregroundPackage = null))
        assertTrue(RearButtonScope.shouldFilter(enabled = true, scopedPackages = emptySet(), foregroundPackage = "com.example.anything"))
    }

    @Test
    fun `enabled with a non-empty allowlist only filters while a listed app is foreground`() {
        val scoped = setOf("com.example.game")
        assertTrue(RearButtonScope.shouldFilter(enabled = true, scopedPackages = scoped, foregroundPackage = "com.example.game"))
        assertFalse(RearButtonScope.shouldFilter(enabled = true, scopedPackages = scoped, foregroundPackage = "com.example.other"))
    }

    @Test
    fun `enabled with a non-empty allowlist and unknown foreground fails closed`() {
        // Bootstrap gap: right after the service (re)connects, before the first window-state event.
        assertFalse(RearButtonScope.shouldFilter(enabled = true, scopedPackages = setOf("com.example.game"), foregroundPackage = null))
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
