package com.madebygps.dothabits.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.madebygps.dothabits.dotApp
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dot Habits Glyph Toy for Nothing Phone (3) (Glyph.DEVICE_23112, 25×25 matrix).
 *
 * Lifecycle follows the GlyphMatrix Developer Kit: the system binds this service when the
 * user selects the toy (short press cycles toys — handled by the system) and unbinds when
 * another toy is selected. Long press arrives as EVENT_CHANGE and ONLY switches between
 * the Today and Timer views; it never completes habits or controls timers.
 * EVENT_AOD arrives once a minute when chosen as the Always-on Glyph Toy.
 *
 * Frames are pushed only when the rendered pixels change; the data tick is one minute
 * (the timer view shows whole minutes), so the toy causes no per-second work.
 */
class GlyphToyService : Service() {

    private var gm: GlyphMatrixManager? = null
    private var scope: CoroutineScope? = null
    private val view = MutableStateFlow(GlyphView.TODAY)
    private val aodTick = MutableStateFlow(0L)
    private var lastFrame: IntArray? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> toggleView()
                GlyphToy.EVENT_AOD -> aodTick.value = System.currentTimeMillis()
                else -> Unit // action_down / action_up: intentionally no behaviour
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
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch { view.value = dotApp.settings.current().glyphView }
        gm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        scope?.cancel()
        scope = null
        gm?.turnOff()
        gm?.unInit()
        gm = null
        lastFrame = null
        return false
    }

    private fun toggleView() {
        val next = if (view.value == GlyphView.TODAY) GlyphView.TIMER else GlyphView.TODAY
        view.value = next
        scope?.launch { dotApp.settings.setGlyphView(next) }
    }

    private fun startRendering() {
        val app = dotApp
        scope?.launch {
            combine(app.repository.snapshots(tickMillis = 60_000L), view, aodTick, app.settings.settings.map { it.glyphBrightness }) { (_, snap), v, aod, brightness ->
                if (snap.activeTimer?.running == true) app.repository.touchAlive()
                Triple(GlyphFrames.render(v, snap), brightness, aod)
            }
                // Skip identical frames, but always answer an AOD tick with a frame.
                .distinctUntilChanged { a, b -> a.first.contentEquals(b.first) && a.second == b.second && a.third == b.third }
                .collect { (frame, brightness, _) -> push(frame, brightness) }
        }
    }

    private fun push(frame: IntArray, brightness: Int) {
        val manager = gm ?: return
        val size = GlyphFrames.SIZE
        val bmp = createBitmap(size, size)
        for (i in frame.indices) {
            val v = frame[i]
            bmp[i % size, i / size] = Color.argb(255, v, v, v)
        }
        try {
            val obj = GlyphMatrixObject.Builder()
                .setImageSource(bmp)
                .setPosition(0, 0)
                .setScale(100)
                .setBrightness(brightness)
                .build()
            val rendered = GlyphMatrixFrame.Builder().addTop(obj).build(applicationContext).render()
            manager.setMatrixFrame(rendered)
            lastFrame = frame
        } catch (e: GlyphException) {
            Log.w(TAG, "setMatrixFrame failed", e)
        }
    }

    private companion object {
        const val TAG = "DotGlyphToy"
    }
}
