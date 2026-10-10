package com.madebygps.dothabits.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.madebygps.dothabits.MainActivity
import com.madebygps.dothabits.data.HABITS_PER_PAGE
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.flow.first

/**
 * How habits are arranged for a given widget size. One-row widgets show as many habits as fit;
 * larger widgets show the first page of six with supporting captions, without habit names.
 */
internal data class WidgetGrid(
    val cols: Int,
    val rows: Int,
    val ringDp: Float,
    val captions: Boolean,
    val visibleHabits: Int,
    /** Column width: columns are packed and centred rather than spread across the full width. */
    val cellDp: Float,
) {
    companion object {
        private const val PADDING_DP = 14f
        private const val CAPTION_DP = 12f

        fun forSize(widthDp: Float, heightDp: Float): WidgetGrid {
            val w = (widthDp - 2 * PADDING_DP).coerceAtLeast(16f)
            val h = (heightDp - 2 * PADDING_DP).coerceAtLeast(16f)
            val (cols, rows) = when {
                h < 82f && w >= 300f -> 6 to 1
                h < 82f && w >= 210f -> 4 to 1
                h < 82f -> 3 to 1
                w >= 1.55f * h -> 3 to 2
                else -> 2 to 3
            }
            val cellW = w / cols
            val cellH = h / rows
            val captions = cellH >= 96f && cellW >= 88f
            val textDp = if (captions) CAPTION_DP else 0f
            val ring = minOf(cellW, cellH - textDp) * 0.90f
            val ringDp = ring.coerceAtLeast(16f)
            return WidgetGrid(
                cols = cols,
                rows = rows,
                ringDp = ringDp,
                captions = captions,
                visibleHabits = cols * rows,
                cellDp = cellW,
            )
        }
    }
}

/**
 * Display-only home-screen widget: the first page of six habit rings, like the app's home grid.
 * The whole widget opens the app; there are intentionally no completion or timer controls.
 * SizeMode.Exact so rings are sized to the launcher's real cell size on Phone (3).
 */
open class DotWidget(private val transparent: Boolean = false) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val started = SystemClock.elapsedRealtime()
        try {
            val app = context.dotApp
            val initialData = app.repository.raw.first()
            val initialSettings = app.settings.current()
            val density = context.resources.displayMetrics.density
            provideContent {
                val data by app.repository.raw.collectAsState(initialData)
                val settings by app.settings.settings.collectAsState(initialSettings)
                Content(app.repository.snapshot(data), density, Color(settings.highlight))
            }
        } finally {
            Log.i("DotHabitsWidget", "widget=grid id=$id sessionMs=${SystemClock.elapsedRealtime() - started}")
        }
    }

    @Composable
    private fun Content(snapshot: TodaySnapshot, density: Float, highlight: Color) {
        val size = LocalSize.current
        val grid = WidgetGrid.forSize(size.width.value, size.height.value)
        val habits = snapshot.habits.take(minOf(HABITS_PER_PAGE, grid.visibleHabits))
        val px = (grid.ringDp * density).toInt().coerceIn(48, 512)
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .let { if (transparent) it else it.background(WidgetColors.background).cornerRadius(8.dp) }
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            if (habits.isEmpty()) {
                Text("DOT HABITS", style = caption(12, WidgetColors.foreground))
                return@Box
            }
            Column(modifier = GlanceModifier.fillMaxSize()) {
                for (r in 0 until grid.rows) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (c in 0 until grid.cols) {
                            val t = habits.getOrNull(r * grid.cols + c)
                            Column(
                                modifier = GlanceModifier.width(grid.cellDp.dp).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (t != null) {
                                    HabitRingImage(t, grid.ringDp.dp, px, highlight)
                                    if (grid.captions) {
                                        val text = HabitLabels.caption(t).ifEmpty { HabitLabels.detail(t) }
                                        if (text.isNotEmpty()) {
                                            Text(text, style = caption(8, WidgetColors.dim), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun caption(sp: Int, color: androidx.glance.unit.ColorProvider) =
        TextStyle(color = color, fontSize = sp.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
}

class DotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotWidget()
}

class TransparentDotWidget : DotWidget(transparent = true)

class DotTransparentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransparentDotWidget()
}
