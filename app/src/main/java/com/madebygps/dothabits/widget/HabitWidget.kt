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
import com.madebygps.dothabits.MainActivity
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.flow.first

internal enum class SingleWidgetPresentation { COMPACT, WIDE, STACKED, LARGE }

/** Layout of the single-habit widget for a given launcher size. */
internal data class SingleLayout(
    val presentation: SingleWidgetPresentation,
    val ringDp: Float,
    val showDetail: Boolean,
    val showCaption: Boolean,
) {
    companion object {
        private const val PADDING_DP = 8f

        fun forSize(widthDp: Float, heightDp: Float): SingleLayout {
            val w = (widthDp - 2 * PADDING_DP).coerceAtLeast(16f)
            val h = (heightDp - 2 * PADDING_DP).coerceAtLeast(16f)
            val presentation = when {
                w < 104f || (h < 56f && w < 180f) -> SingleWidgetPresentation.COMPACT
                h < 125f || w >= 1.45f * h -> SingleWidgetPresentation.WIDE
                w >= 220f && h >= 220f -> SingleWidgetPresentation.LARGE
                else -> SingleWidgetPresentation.STACKED
            }
            val ringDp = when (presentation) {
                SingleWidgetPresentation.COMPACT -> minOf(w, h)
                SingleWidgetPresentation.WIDE -> minOf(h, w * 0.34f)
                SingleWidgetPresentation.STACKED -> minOf(w, h * 0.62f)
                SingleWidgetPresentation.LARGE -> minOf(w * 0.58f, h * 0.58f)
            }.coerceAtLeast(16f)
            return SingleLayout(
                presentation = presentation,
                ringDp = ringDp,
                showDetail = presentation != SingleWidgetPresentation.COMPACT,
                showCaption = presentation == SingleWidgetPresentation.LARGE ||
                    (presentation == SingleWidgetPresentation.WIDE && w >= 220f) ||
                    (presentation == SingleWidgetPresentation.STACKED && h >= 190f),
            )
        }
    }
}

/**
 * Display-only widget for one habit the user picks when placing it (and can change with the
 * launcher's reconfigure option). Compact sizes show the ring, while larger sizes add the habit
 * name, progress detail and essential state captions. Tapping opens that habit's detail
 * screen; there are no completion or timer controls.
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
            val detail = HabitLabels.detail(t)
            val habitCaption = HabitLabels.caption(t).takeUnless { it == detail }.orEmpty()
            when (layout.presentation) {
                SingleWidgetPresentation.COMPACT -> HabitRingImage(t, layout.ringDp.dp, px)
                SingleWidgetPresentation.WIDE -> Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HabitRingImage(t, layout.ringDp.dp, px)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HabitText(t.habit.name, detail, habitCaption, layout, large = false)
                    }
                }
                SingleWidgetPresentation.STACKED,
                SingleWidgetPresentation.LARGE,
                -> Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HabitRingImage(t, layout.ringDp.dp, px)
                    Spacer(GlanceModifier.height(if (layout.presentation == SingleWidgetPresentation.LARGE) 10.dp else 6.dp))
                    HabitText(
                        t.habit.name,
                        detail,
                        habitCaption,
                        layout,
                        large = layout.presentation == SingleWidgetPresentation.LARGE,
                    )
                }
            }
        }
    }

    @Composable
    private fun HabitText(
        name: String,
        detail: String,
        habitCaption: String,
        layout: SingleLayout,
        large: Boolean,
    ) {
        Text(
            name.uppercase(),
            style = caption(if (large) 14 else 12, WidgetColors.foreground),
            maxLines = 1,
        )
        if (layout.showDetail && detail.isNotEmpty()) {
            Text(detail, style = caption(if (large) 10 else 9, WidgetColors.dim), maxLines = 1)
        }
        if (layout.showCaption && habitCaption.isNotEmpty()) {
            Text(habitCaption, style = caption(if (large) 10 else 9, WidgetColors.dim), maxLines = 1)
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

class HabitWideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
