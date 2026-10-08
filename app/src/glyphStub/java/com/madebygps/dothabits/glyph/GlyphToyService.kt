package com.madebygps.dothabits.glyph

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder compiled when the Glyph Matrix SDK AAR is not present in app/libs.
 * The manifest entry is disabled in that case (@bool/glyph_toy_enabled = false), so the
 * system never lists or binds this toy.
 */
class GlyphToyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
