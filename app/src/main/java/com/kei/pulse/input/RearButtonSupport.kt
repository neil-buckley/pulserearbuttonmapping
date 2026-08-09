package com.kei.pulse.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.core.content.getSystemService
import com.kei.pulse.model.RearButtons

/**
 * Detects the Odin's rear-button hardware directly (see [RearButtons] for the scancode facts) rather than
 * gating on `ro.soc.model` — the SoC is shared with non-Odin devices ([com.kei.pulse.model.DeviceProfiles]
 * resolves the Odin 2's `QCS8550` to the same row as Retroid Pocket 6), so it can't tell them apart. An
 * attached gamepad from AYN's MCU (`vendorId 0x2020`) that actually exposes `BUTTON_C`/`BUTTON_Z` is
 * self-describing and covers Odin 2/3 + Thor without a model list.
 */
object RearButtonSupport {

    private const val AYN_VENDOR_ID = 0x2020

    fun isSupported(context: Context): Boolean {
        val inputManager = context.getSystemService<InputManager>() ?: return false
        return inputManager.inputDeviceIds.any { id ->
            val device = inputManager.getInputDevice(id) ?: return@any false
            device.vendorId == AYN_VENDOR_ID && hasRearButtons(device)
        }
    }

    private fun hasRearButtons(device: InputDevice): Boolean {
        val keyCodes = RearButtons.ALL.map { it.keyCode }.toIntArray()
        return device.hasKeys(*keyCodes).all { it }
    }
}
