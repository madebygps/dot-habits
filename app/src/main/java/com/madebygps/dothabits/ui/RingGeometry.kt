package com.madebygps.dothabits.ui

/**
 * Ring geometry shared by the Compose UI and the widget Canvas renderer so both look identical.
 * Angles are in degrees, starting at 12 o'clock (-90°) and going clockwise.
 */
object RingGeometry {
    const val STROKE_FRACTION = 0.075f
    const val ICON_FRACTION = 0.40f
    const val DOT_RADIUS_FRACTION = 0.40f
    private const val SEGMENT_GAP_DEG = 8f

    data class Arcs(val track: List<Pair<Float, Float>>, val filled: List<Pair<Float, Float>>)

    fun arcs(fraction: Float, segments: Int): Arcs {
        val f = fraction.coerceIn(0f, 1f)
        if (segments < 2) {
            return Arcs(listOf(-90f to 360f), if (f > 0f) listOf(-90f to 360f * f) else emptyList())
        }
        val each = 360f / segments
        val sweep = each - SEGMENT_GAP_DEG
        val track = (0 until segments).map { i -> (-90f + SEGMENT_GAP_DEG / 2 + i * each) to sweep }
        // Fill whole segments first, then a partial one, so "1 of 2" reads clearly.
        val filledUnits = f * segments
        val filled = track.mapIndexedNotNull { i, (start, sw) ->
            val portion = (filledUnits - i).coerceIn(0f, 1f)
            if (portion > 0f) start to sw * portion else null
        }
        return Arcs(track, filled)
    }
}
