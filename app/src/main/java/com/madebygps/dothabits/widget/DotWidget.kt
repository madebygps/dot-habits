package com.madebygps.dothabits.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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
import com.madebygps.dothabits.data.HABITS_PER_PAGE
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.flow.first

private val White = ColorProvider(Color(0xFFF2F2F2))
private val Grey = ColorProvider(Color(0xFF8A8A8A))

private data class WidgetModel(
    val snapshot: TodaySnapshot,
    val highlight: Int,
    val rings: List<Pair<Bitmap, String>>,
    val overall: Bitmap,
)

/**
 * Display-only home-screen widget. It shows the same snapshot as the app; the whole
 * widget opens the app. There are intentionally no completion or timer controls.
 */
class DotWidget : GlanceAppWidget() {

    companion object {
        // Responsive buckets tuned for Nothing Launcher on Phone (3); verify on device.
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 230.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.dotApp
        val data = app.repository.raw.first()
        val snapshot = app.repository.snapshot(data)
        val highlight = data.settings.highlight.toInt()
        val firstPage = snapshot.habits.take(HABITS_PER_PAGE)
        val model = WidgetModel(
            snapshot = snapshot,
            highlight = highlight,
            rings = firstPage.map { RingBitmaps.habit(it, 160, highlight) to it.habit.name },
            overall = RingBitmaps.ring(220, snapshot.overallFraction, 0, highlight),
        )
        provideContent { Content(model) }
    }

    @Composable
    private fun Content(m: WidgetModel) {
        val size = LocalSize.current
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Color.Black))
                .cornerRadius(28.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            when {
                m.snapshot.habits.isEmpty() -> Text("DOT HABITS", style = caption(12, White))
                size.width >= LARGE.width && size.height >= LARGE.height -> Large(m)
                size.width >= WIDE.width -> Wide(m)
                else -> Small(m)
            }
        }
    }

    @Composable
    private fun Small(m: WidgetModel) {
        Box(contentAlignment = Alignment.Center) {
            Image(ImageProvider(m.overall), "Today's progress", modifier = GlanceModifier.size(92.dp))
            Text("${m.snapshot.doneCount}/${m.snapshot.dueCount}", style = caption(20, White))
        }
    }

    @Composable
    private fun Wide(m: WidgetModel) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            m.rings.forEachIndexed { i, (bmp, name) ->
                if (i > 0) Spacer(GlanceModifier.width(4.dp))
                Image(ImageProvider(bmp), name, modifier = GlanceModifier.defaultWeight().height(52.dp))
            }
        }
    }

    @Composable
    private fun Large(m: WidgetModel) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text("TODAY ${m.snapshot.doneCount}/${m.snapshot.dueCount}", style = caption(11, Grey))
            Spacer(GlanceModifier.height(6.dp))
            m.rings.chunked(3).forEach { row ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEach { (bmp, name) ->
                        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(ImageProvider(bmp), name, modifier = GlanceModifier.size(60.dp))
                            Text(name, style = caption(10, White), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    private fun caption(sp: Int, color: ColorProvider) =
        TextStyle(color = color, fontSize = sp.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
}

class DotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotWidget()
}
