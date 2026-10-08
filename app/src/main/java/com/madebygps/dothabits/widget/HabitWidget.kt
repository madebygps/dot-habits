package com.madebygps.dothabits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.madebygps.dothabits.MainActivity
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.HabitToday
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.flow.first

private val White = ColorProvider(Color(0xFFF2F2F2))
private val Grey = ColorProvider(Color(0xFF8A8A8A))

/** Layout of the single-habit widget for a given size. */
internal data class SingleLayout(val kind: Kind, val ringDp: Float) {
    enum class Kind {
        /** Ring only (1×1 and other small sizes). */
        RING,
        /** Ring with name and streak to its right (wide, short sizes such as 2×1 or 4×1). */
        WIDE,
        /** Ring with name and streak underneath (2×2 and larger). */
        TALL,
    }

    companion object {
        private const val PADDING_DP = 8f
        private const val TEXT_DP = 30f

        fun forSize(widthDp: Float, heightDp: Float): SingleLayout {
            val w = widthDp - 2 * PADDING_DP
            val h = heightDp - 2 * PADDING_DP
            return when {
                w >= 1.7f * h && w >= 150f -> SingleLayout(Kind.WIDE, minOf(h, w * 0.45f).coerceAtLeast(16f))
                h >= 120f && w >= 96f -> SingleLayout(Kind.TALL, minOf(w, h - TEXT_DP).coerceAtLeast(16f))
                else -> SingleLayout(Kind.RING, minOf(w, h).coerceAtLeast(16f))
            }
        }
    }
}

/**
 * Display-only widget for one habit the user picks when placing it (and can change with the
 * launcher's reconfigure option). Resizable from 1×1 (ring only) to wide or large layouts.
 * Tapping opens that habit's detail screen; there are no completion or timer controls.
 */
class HabitWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.dotApp
        val data = app.repository.raw.first()
        val snapshot = app.repository.snapshot(data)
        val highlight = data.settings.highlight.toInt()
        val density = context.resources.displayMetrics.density
        provideContent {
            val habitId = currentState<Preferences>()[HABIT_ID]
            Content(snapshot, habitId, highlight, density)
        }
    }

    @Composable
    private fun Content(snapshot: TodaySnapshot, habitId: Long?, highlight: Int, density: Float) {
        val size = LocalSize.current
        val layout = SingleLayout.forSize(size.width.value, size.height.value)
        val t = snapshot.habits.firstOrNull { it.habit.id == habitId }
        val open = if (t != null) {
            actionStartActivity<MainActivity>(actionParametersOf(HABIT_PARAM to t.habit.id))
        } else actionStartActivity<MainActivity>()
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Color.Black))
                .cornerRadius(28.dp)
                .padding(8.dp)
                .clickable(open),
            contentAlignment = Alignment.Center,
        ) {
            if (t == null) {
                Text(
                    if (habitId == null) "PICK A HABIT" else "HABIT REMOVED",
                    style = caption(10, Grey),
                )
                return@Box
            }
            val px = (layout.ringDp * density).toInt().coerceIn(48, 512)
            val ring = @Composable {
                Image(
                    ImageProvider(RingBitmaps.habit(t, px, highlight)),
                    t.habit.name,
                    modifier = GlanceModifier.size(layout.ringDp.dp),
                )
            }
            when (layout.kind) {
                SingleLayout.Kind.RING -> ring()
                SingleLayout.Kind.TALL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ring()
                    Spacer(GlanceModifier.height(4.dp))
                    Labels(t, TextAlign.Center)
                }
                SingleLayout.Kind.WIDE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    ring()
                    Spacer(GlanceModifier.width(12.dp))
                    Column { Labels(t, TextAlign.Start) }
                }
            }
        }
    }

    @Composable
    private fun Labels(t: HabitToday, align: TextAlign) {
        Text(t.habit.name.uppercase(), style = caption(11, White, align), maxLines = 1)
        val line = HabitLabels.caption(t)
        if (line.isNotEmpty()) Text(line, style = caption(9, Grey, align), maxLines = 1)
    }

    private fun caption(sp: Int, color: ColorProvider, align: TextAlign = TextAlign.Center) =
        TextStyle(color = color, fontSize = sp.sp, fontFamily = FontFamily.Monospace, textAlign = align)

    companion object {
        val HABIT_ID = longPreferencesKey("habit_id")
        private val HABIT_PARAM = androidx.glance.action.ActionParameters.Key<Long>(MainActivity.EXTRA_HABIT_ID)
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
