package com.madebygps.dothabits.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.madebygps.dothabits.MainActivity
import com.madebygps.dothabits.data.HABITS_PER_PAGE
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.flow.first

/**
 * How six habits are arranged for a given widget size. Square-ish sizes (the default 2×2) use
 * the app's own 2-column × 3-row grid; wide sizes switch to 3×2 or a single row of six.
 */
internal data class WidgetGrid(
    val cols: Int,
    val rows: Int,
    val ringDp: Float,
    val labels: Boolean,
    /** Column width: columns are packed and centred rather than spread across the full width. */
    val cellDp: Float,
) {
    companion object {
        private const val PADDING_DP = 10f
        private const val LABEL_DP = 14f
        private const val GAP_DP = 4f

        fun forSize(widthDp: Float, heightDp: Float): WidgetGrid {
            val w = widthDp - 2 * PADDING_DP
            val h = heightDp - 2 * PADDING_DP
            val (cols, rows) = when {
                w >= 2.6f * h -> 6 to 1
                w >= 1.3f * h -> 3 to 2
                else -> 2 to 3
            }
            val cellW = w / cols
            val cellH = h / rows
            val labels = cellH >= 76f && cellW >= 72f
            val ring = minOf(cellW, cellH - if (labels) LABEL_DP else 0f) * 0.88f
            val ringDp = ring.coerceAtLeast(16f)
            val cell = minOf(cellW, ringDp + 2 * GAP_DP + if (labels) 24f else 0f)
            return WidgetGrid(cols, rows, ringDp, labels, cell)
        }
    }
}

/**
 * Display-only home-screen widget: the first page of six habit rings, like the app's home grid.
 * The whole widget opens the app; there are intentionally no completion or timer controls.
 * SizeMode.Exact so rings are sized to the launcher's real cell size on Phone (3).
 */
class DotWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val started = SystemClock.elapsedRealtime()
        try {
            val app = context.dotApp
            val data = app.repository.raw.first()
            val snapshot = app.repository.snapshot(data)
            val density = context.resources.displayMetrics.density
            provideContent { Content(snapshot, density) }
        } finally {
            Log.i("DotHabitsWidget", "widget=grid id=$id sessionMs=${SystemClock.elapsedRealtime() - started}")
        }
    }

    @Composable
    private fun Content(snapshot: TodaySnapshot, density: Float) {
        val size = LocalSize.current
        val grid = WidgetGrid.forSize(size.width.value, size.height.value)
        val habits = snapshot.habits.take(HABITS_PER_PAGE)
        val px = (grid.ringDp * density).toInt().coerceIn(48, 256)
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(28.dp)
                .padding(10.dp)
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
                                    HabitRingImage(t, grid.ringDp.dp, px)
                                    if (grid.labels) Text(t.habit.name.uppercase(), style = caption(9, WidgetColors.dim), maxLines = 1)
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
