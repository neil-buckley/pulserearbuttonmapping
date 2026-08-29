package com.kei.pulse.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.getSystemService
import com.kei.pulse.AppContainer
import com.kei.pulse.model.AppSettings
import com.kei.pulse.model.RearButtonAction
import com.kei.pulse.model.RearButtonMapper
import com.kei.pulse.model.RearButtonOutcome
import com.kei.pulse.model.RearButtonScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin shell around [RearButtonMapper]: reads the Odin's M1/M2 as ordinary gamepad KeyEvents
 * (`KEYCODE_BUTTON_C`/`KEYCODE_BUTTON_Z` — see [com.kei.pulse.model.RearButtons]) and, when the feature is
 * enabled, consumes the button and fires the configured action instead of letting the foreground app see it.
 *
 * All decision logic lives in [RearButtonMapper] (pure, unit-tested); this class only wires it to Android:
 * AudioManager for volume, `dispatchMediaKeyEvent` for media, `performGlobalAction` for nav. [onKeyEvent]
 * runs on the main thread for every key on the device, so it must never block — settings are cached from a
 * background collector into a `@Volatile` snapshot rather than read synchronously.
 *
 * `FLAG_REQUEST_FILTER_KEY_EVENTS` (declared as a manifest CAPABILITY in `odin_button_service.xml`, but no
 * longer a static flag there) is a global tax: while requested, Android routes EVERY key event on the
 * device, for every foreground app, through this service — not just M1/M2. [RearButtonScope] decides when
 * that's actually worth paying for, and [applyFilterScope] toggles the flag at runtime via `setServiceInfo`
 * so it's only held while the master switch is on and (in Selected-apps scope) a listed app is
 * foreground. Foreground tracking rides the `typeWindowStateChanged` events the service already receives
 * (declared in the XML) — cheaper and more immediate than `ForegroundAppMonitorService`'s UsageStats
 * polling, and needs no extra permission.
 */
class OdinButtonService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container by lazy { AppContainer(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var settings: AppSettings = AppSettings()
    @Volatile private var foregroundPackage: String? = null

    /** Cached last-applied state so `setServiceInfo` (a Binder round-trip) only fires on an actual change. */
    @Volatile private var filterActive = false
    private var repeatRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            container.settingsStorage.settings.collect {
                settings = it
                applyFilterScope()
            }
        }
        // foregroundPackage starts null (no getWindows()/FLAG_RETRIEVE_INTERACTIVE_WINDOWS bootstrap — that's
        // a second, broader capability Android surfaces distinctly to the user, not worth it for a gap that's
        // milliseconds long). In Selected-apps scope this fails closed until the first window-state event
        // arrives; in Everywhere scope it's irrelevant since foreground isn't consulted.
        applyFilterScope()
    }

    /** Foreground-app tracking for [RearButtonScope] — the only consumer of the window-state events this
     * service already receives (declared in odin_button_service.xml) that were previously discarded. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (RearButtonScope.isTransientSystemWindow(pkg)) return
        if (pkg == foregroundPackage) return
        foregroundPackage = pkg
        // The app the user was holding a button for just changed underneath them — don't keep firing the
        // action into whatever is foreground now.
        stopRepeat()
        applyFilterScope()
    }

    private fun applyFilterScope() {
        val current = settings
        val want = RearButtonScope.shouldFilter(
            enabled = current.rearButtonsEnabled,
            mode = current.rearButtonScopeMode,
            scopedPackages = current.rearButtonScopedPackages,
            foregroundPackage = foregroundPackage,
        )
        if (want == filterActive) return
        filterActive = want
        val info = serviceInfo ?: return
        info.flags = if (want) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        val current = settings
        val outcome = RearButtonMapper.map(
            enabled = current.rearButtonsEnabled,
            m1 = current.rearButtonM1,
            m2 = current.rearButtonM2,
            keyCode = event.keyCode,
            down = down,
        )
        return when (outcome) {
            RearButtonOutcome.PassThrough -> false
            is RearButtonOutcome.Consume -> {
                val action = outcome.action
                if (action != null) {
                    perform(action)
                    if (event.repeatCount == 0 && RearButtonMapper.isRepeatable(action)) startRepeat(action)
                } else {
                    stopRepeat()
                }
                true
            }
        }
    }

    private fun startRepeat(action: RearButtonAction) {
        stopRepeat()
        val runnable = object : Runnable {
            override fun run() {
                perform(action)
                mainHandler.postDelayed(this, RearButtonMapper.REPEAT_INTERVAL_MS)
            }
        }
        repeatRunnable = runnable
        mainHandler.postDelayed(runnable, RearButtonMapper.REPEAT_INITIAL_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatRunnable?.let(mainHandler::removeCallbacks)
        repeatRunnable = null
    }

    private fun perform(action: RearButtonAction) {
        when (action) {
            RearButtonAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            RearButtonAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            RearButtonAction.MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            RearButtonAction.MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            RearButtonAction.MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            RearButtonAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            RearButtonAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            RearButtonAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            RearButtonAction.NONE -> Unit
        }
    }

    /** Deliberately `adjustStreamVolume` + `FLAG_SHOW_UI` (unlike the silent `setStreamVolume` the Quick Access
     * "System" slider uses) — a physical button press should show the volume UI like any hardware rocker. */
    private fun adjustVolume(direction: Int) {
        getSystemService<AudioManager>()?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = getSystemService<AudioManager>() ?: return
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stopRepeat()
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = ComponentName(context, OdinButtonService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
