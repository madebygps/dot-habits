package com.madebygps.dothabits.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.madebygps.dothabits.dotApp
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Display-only selected habit. Button events are left to system toy cycling. */
class HabitGlyphToyService : Service() {
    private var manager: GlyphMatrixManager? = null
    private var scope: CoroutineScope? = null
    private val tick = MutableStateFlow(0L)
    private val aodTick = MutableStateFlow(0L)
    @Volatile private var fastTick = false

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            if (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) == GlyphToy.EVENT_AOD) {
                aodTick.value += 1
            }
        }
    })

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            val gm = manager ?: return
            if (!gm.register(Glyph.DEVICE_23112)) Log.w("DotHabitGlyph", "register(DEVICE_23112) returned false")
            startRendering()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scope?.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        manager = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return false
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        scope?.cancel()
        scope = null
        manager?.turnOff()
        manager?.unInit()
        manager = null
    }

    private fun startRendering() {
        val app = dotApp
        val s = scope ?: return
        s.launch {
            var lastEmit = 0L
            while (true) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                if (fastTick || now - lastEmit >= 60_000L) {
                    tick.value = now
                    lastEmit = now
                }
            }
        }
        s.launch {
            var last: IntArray? = null
            var lastAod = -1L
            combine(app.repository.raw, tick, aodTick) { data, _, aod -> data to aod }
                .collect { (data, aod) ->
                    val snapshot = app.repository.snapshot(data)
                    val habit = snapshot.glyphHabit(data.settings.glyphHabitId)
                    fastTick = habit?.timerRunning == true || habit?.hasData == false
                    val frame = GlyphFrames.habit(snapshot, data.settings.glyphHabitId, System.currentTimeMillis() / 1_000L)
                    if (last?.contentEquals(frame) != true || aod != lastAod) {
                        manager?.pushMonochromeFrame(applicationContext, frame)
                        last = frame
                        lastAod = aod
                    }
                }
        }
    }
}
