package com.madebygps.dothabits.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.SystemClock
import android.util.Log
import com.madebygps.dothabits.domain.ActiveTimer
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.dotApp
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Dot Habits Glyph Toy for Nothing Phone (3) (Glyph.DEVICE_23112, 25×25 matrix): a timer for
 * timed habits.
 *
 * Lifecycle follows the GlyphMatrix Developer Kit: the system binds this service when the
 * user selects the toy (short press cycles toys — handled by the system) and unbinds when
 * another toy is selected. Long press arrives as EVENT_CHANGE and starts or pauses the timer the
 * toy shows ([com.madebygps.dothabits.domain.TodaySnapshot.activeTimer]).
 *
 * Holding past [HOLD_TO_SWITCH_MS] instead switches to the next timed habit (measured between the
 * documented action_down / action_up events; tested on a Phone (3): change arrives ~0.5 s after
 * action_down, action_up on release). The action is decided on release so a hold never also
 * toggles. A press without EVENT_CHANGE is ignored, as the system
 * didn't treat it as a long press. Switching is disabled while a timer runs. It never logs
 * completions directly; time only counts while a timer runs, exactly as in the app.
 * EVENT_AOD arrives once a minute when chosen as the Always-on Glyph Toy.
 *
 * Frames are pushed only when the rendered pixels change. The data tick is one second while a
 * timer runs (the countdown and ring move) and one minute otherwise.
 */
class GlyphToyService : Service() {

    private var gm: GlyphMatrixManager? = null
    private var scope: CoroutineScope? = null
    private val tick = MutableStateFlow(0L)
    private val aodTick = MutableStateFlow(0L)
    @Volatile private var running = false
    private val redraw = MutableStateFlow(0L)
    @Volatile private var overlay = false

    // Button state, touched only on the main looper.
    private var downAt = 0L
    private var changeSeen = false

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_ACTION_DOWN -> onDown()
                GlyphToy.EVENT_CHANGE -> onChange()
                GlyphToy.EVENT_ACTION_UP -> onUp()
                GlyphToy.EVENT_AOD -> aodTick.value = System.currentTimeMillis()
                else -> Unit
            }
        }
    }
    private val messenger = Messenger(handler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            val manager = gm ?: return
            if (!manager.register(Glyph.DEVICE_23112)) Log.w(TAG, "register(DEVICE_23112) returned false")
            startRendering()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scope?.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        gm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        scope?.cancel()
        scope = null
        gm?.turnOff()
        gm?.unInit()
        gm = null
        return false
    }

    private fun onDown() {
        downAt = SystemClock.elapsedRealtime()
        changeSeen = false
    }

    private fun onChange() {
        val stale = downAt == 0L || SystemClock.elapsedRealtime() - downAt > STALE_DOWN_MS
        if (stale) {
            // No matching action_down: fall back to the documented long-press meaning.
            downAt = 0L
            toggleTimer()
        } else {
            changeSeen = true
        }
    }

    private fun onUp() {
        if (downAt != 0L && changeSeen) {
            if (SystemClock.elapsedRealtime() - downAt >= HOLD_TO_SWITCH_MS) switchTimer()
            else toggleTimer()
        }
        downAt = 0L
        changeSeen = false
    }

    private fun toggleTimer() {
        val app = dotApp
        scope?.launch {
            val target = app.repository.currentSnapshot(choice.value).activeTimer ?: run {
                buzz(Haptic.NOTHING); return@launch
            }
            buzz(if (target.running) Haptic.PAUSE else Haptic.START)
            app.repository.toggleTimer(target.habitId)
        }
    }

    private fun switchTimer() {
        val app = dotApp
        scope?.launch {
            val next = app.repository.currentSnapshot(choice.value).activeTimer?.next ?: run {
                buzz(Haptic.NOTHING); return@launch
            }
            buzz(Haptic.SWITCH)
            choice.value = next
            overlay = true
            try {
                push(GlyphFrames.picked(app.repository.currentSnapshot(next)))
                delay(PICKED_MS)
            } finally {
                overlay = false
                redraw.value = System.currentTimeMillis()
            }
        }
    }

    private enum class Haptic { START, PAUSE, SWITCH, NOTHING }

    /** Distinct haptics per toy action (start, pause, switch, nothing to do). */
    private fun buzz(kind: Haptic) {
        val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = when (kind) {
            Haptic.START -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            Haptic.PAUSE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            Haptic.SWITCH -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Haptic.NOTHING -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30, 60, 30), -1)
        }
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
    }

    private fun startRendering() {
        val app = dotApp
        val s = scope ?: return
        s.launch {
            // Wakes every second but only re-renders every second while a timer runs; once a
            // minute otherwise. Coroutine delays don't hold a wake lock, so this costs nothing asleep.
            var lastEmit = 0L
            while (true) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                if (running || now - lastEmit >= 60_000L) {
                    tick.value = now
                    lastEmit = now
                }
            }
        }
        s.launch {
            var last: IntArray? = null
            var lastForce = 0L
            var previous: ActiveTimer? = null
            combine(app.repository.raw, tick, aodTick, choice, redraw) { data, _, aod, pick, force ->
                Triple(data, aod + force, pick)
            }.collect { (data, force, pick) ->
                val snap = app.repository.snapshot(data, preferredTimer = pick)
                val t = snap.activeTimer
                running = t?.running == true
                if (running) app.repository.touchAlive()
                if (previous?.running == true && t?.habitId != previous?.habitId || (previous?.running == true && t?.running == false)) {
                    // The timer stopped; if it was because the session ended, persist it and celebrate.
                    val prev = previous!!
                    val finishedNow = prev.endsAt?.let { !java.time.Instant.now().isBefore(it) }
                        ?: (snap.habits.firstOrNull { it.habit.id == prev.habitId }
                            ?.let { TimerMath.sessionsDone(it.value, it.habit.sessionSeconds, it.habit.sessions) > prev.sessionsDone } == true)
                    if (finishedNow) {
                        app.repository.finishElapsedSessions()
                        celebrate()
                        last = null
                    }
                }
                previous = t
                val frame = GlyphFrames.render(snap)
                // Skip identical frames, but always answer an AOD tick (or redraw) with a frame.
                if (!overlay && (last?.contentEquals(frame) != true || force != lastForce)) {
                    push(frame)
                    last = frame
                    lastForce = force
                }
            }
        }
    }

    private suspend fun celebrate() {
        val frames = GlyphFrames.celebration()
        frames.forEachIndexed { i, f ->
            push(f)
            delay(if (i == frames.lastIndex) 2_000L else 90L)
        }
    }

    private fun push(frame: IntArray) {
        gm?.pushMonochromeFrame(applicationContext, frame)
    }

    private companion object {
        const val TAG = "DotGlyphToy"
        const val HOLD_TO_SWITCH_MS = 2_000L
        const val STALE_DOWN_MS = 10_000L
        const val PICKED_MS = 1_200L

        /** Timer picked with the hold gesture; kept for the process so re-selecting the toy keeps it. */
        val choice = MutableStateFlow<Long?>(null)
    }
}
