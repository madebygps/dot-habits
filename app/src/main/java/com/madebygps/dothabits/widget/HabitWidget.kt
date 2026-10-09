package com.madebygps.dothabits.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.madebygps.dothabits.MainActivity
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.flow.first

/** Layout of the single-habit widget for a given size. */
internal data class SingleLayout(val ringDp: Float) {
    companion object {
        private const val PADDING_DP = 8f

        fun forSize(widthDp: Float, heightDp: Float): SingleLayout {
            val w = widthDp - 2 * PADDING_DP
            val h = heightDp - 2 * PADDING_DP
            return SingleLayout(minOf(w, h).coerceAtLeast(16f))
        }
    }
}

/**
 * Display-only widget for one habit the user picks when placing it (and can change with the
 * launcher's reconfigure option). Every size shows only the habit icon and progress ring.
 * Tapping opens that habit's detail screen; there are no completion or timer controls.
 */
class HabitWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val started = SystemClock.elapsedRealtime()
        try {
            val app = context.dotApp
            val data = app.repository.raw.first()
            val snapshot = app.repository.snapshot(data)
            val density = context.resources.displayMetrics.density
            provideContent {
                val habitId = currentState<Preferences>()[HABIT_ID]
                Content(snapshot, habitId, density)
            }
        } finally {
            Log.i("DotHabitsWidget", "widget=habit id=$id sessionMs=${SystemClock.elapsedRealtime() - started}")
        }
    }

    @Composable
    private fun Content(snapshot: TodaySnapshot, habitId: Long?, density: Float) {
        val size = LocalSize.current
        val layout = SingleLayout.forSize(size.width.value, size.height.value)
        val t = snapshot.habits.firstOrNull { it.habit.id == habitId }
        val open = if (t != null) {
            actionStartActivity<MainActivity>(actionParametersOf(HABIT_PARAM to t.habit.id))
        } else actionStartActivity<MainActivity>()
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(28.dp)
                .padding(8.dp)
                .clickable(open),
            contentAlignment = Alignment.Center,
        ) {
            if (t == null) {
                Text(
                    if (habitId == null) "PICK A HABIT" else "HABIT REMOVED",
                    style = caption(10, WidgetColors.dim),
                )
                return@Box
            }
            val px = (layout.ringDp * density).toInt().coerceIn(48, 512)
            HabitRingImage(t, layout.ringDp.dp, px)
        }
    }

    private fun caption(sp: Int, color: androidx.glance.unit.ColorProvider, align: TextAlign = TextAlign.Center) =
        TextStyle(color = color, fontSize = sp.sp, fontFamily = FontFamily.Monospace, textAlign = align)

    companion object {
        val HABIT_ID = longPreferencesKey("habit_id")
        private val HABIT_PARAM = androidx.glance.action.ActionParameters.Key<Long>(MainActivity.EXTRA_HABIT_ID)
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
