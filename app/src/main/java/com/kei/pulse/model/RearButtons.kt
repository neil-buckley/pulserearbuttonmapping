package com.kei.pulse.model

/** Action an Odin rear button can be mapped to. [NONE] leaves the button untouched (passed through to the app). */
enum class RearButtonAction(val label: String) {
    NONE("None"),
    VOLUME_UP("Volume up"),
    VOLUME_DOWN("Volume down"),
    MEDIA_PLAY_PAUSE("Play / pause"),
    MEDIA_NEXT("Next track"),
    MEDIA_PREVIOUS("Previous track"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("App switcher"),
}

/** One physical rear button, identified by the Android keycode it arrives as. */
data class RearButton(val label: String, val keyCode: Int)

/**
 * The AYN Odin's rear M1/M2 buttons as DATA — scancode facts probed 2026-08-09 on an Odin 2 Portal
 * (`ro.soc.model=QCS8550`, Android 13). The MCU exposes them as a gamepad ("Xbox Wireless Controller",
 * `vendor=0x2020 product=0x0112`, see [com.kei.pulse.input.RearButtonSupport]); its key layout
 * (`/system/usr/keylayout/Vendor_2020_Product_0112.kl`) maps `0x132` to `BUTTON_C` and `0x135` to
 * `BUTTON_Z`, which Android's InputReader turns into these two ordinary gamepad KeyEvents — no root,
 * no `/dev/input` access, and nothing vendor-specific needed to read them.
 */
object RearButtons {
    /** M1 — `BTN_C` (0x132) → `KEYCODE_BUTTON_C`. */
    val M1 = RearButton("M1", keyCode = 98)

    /** M2 — `BTN_Z` (0x135) → `KEYCODE_BUTTON_Z`. */
    val M2 = RearButton("M2", keyCode = 101)

    val ALL = listOf(M1, M2)

    fun forKeyCode(keyCode: Int): RearButton? = ALL.firstOrNull { it.keyCode == keyCode }
}
