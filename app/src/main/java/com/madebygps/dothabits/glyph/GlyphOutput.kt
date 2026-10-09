package com.madebygps.dothabits.glyph

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

internal fun GlyphMatrixManager.pushMonochromeFrame(context: Context, frame: IntArray) {
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
            .build()
        val rendered = GlyphMatrixFrame.Builder().addTop(obj).build(context).render()
        setMatrixFrame(rendered)
    } catch (e: GlyphException) {
        Log.w("DotGlyphToy", "setMatrixFrame failed", e)
    }
}
