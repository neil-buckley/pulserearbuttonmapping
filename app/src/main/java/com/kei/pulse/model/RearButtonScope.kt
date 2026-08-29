package com.kei.pulse.model

/** Where the M1/M2 remap applies: system-wide, or only while one of the chosen apps is foreground. */
enum class RearButtonScopeMode(val label: String) {
    EVERYWHERE("Everywhere"),
    SELECTED_APPS("Selected apps"),
}

/**
 * Decides whether [com.kei.pulse.input.OdinButtonService] should currently be requesting
 * `FLAG_REQUEST_FILTER_KEY_EVENTS` — i.e. whether Android should route every key event on the device
 * through the service at all. That flag is a global latency tax on EVERY app while requested (Android
 * doesn't scope it to specific keycodes), so it must only be held while it can actually do something:
 * the master switch is on, and the [RearButtonScopeMode] says this foreground app qualifies. In
 * [RearButtonScopeMode.SELECTED_APPS] an empty allowlist fails closed (nothing remapped) rather than
 * silently meaning "everywhere".
 */
object RearButtonScope {
    fun shouldFilter(
        enabled: Boolean,
        mode: RearButtonScopeMode,
        scopedPackages: Set<String>,
        foregroundPackage: String?,
    ): Boolean {
        if (!enabled) return false
        if (mode == RearButtonScopeMode.EVERYWHERE) return true
        return foregroundPackage != null && foregroundPackage in scopedPackages
    }

    /**
     * `com.android.systemui` (volume panel, notification shade, recents, screenshot UI, …) fires its own
     * `TYPE_WINDOW_STATE_CHANGED` events. Found on-device: pressing a volume-mapped button shows the volume
     * panel via `FLAG_SHOW_UI`, which fired one of these — read as "left the scoped app," so the very next
     * press was already unfiltered. These aren't real foreground-app changes and must be ignored by whatever
     * is tracking [foregroundPackage].
     */
    private val TRANSIENT_SYSTEM_PACKAGES = setOf("com.android.systemui", "android")

    fun isTransientSystemWindow(packageName: String): Boolean = packageName in TRANSIENT_SYSTEM_PACKAGES
}
