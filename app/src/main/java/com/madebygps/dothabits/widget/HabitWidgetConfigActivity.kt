package com.madebygps.dothabits.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.madebygps.dothabits.data.AppSettings
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.domain.TodayStatus
import com.madebygps.dothabits.dotApp
import com.madebygps.dothabits.ui.DotIcon
import com.madebygps.dothabits.ui.DotTheme
import com.madebygps.dothabits.ui.Palette
import com.madebygps.dothabits.ui.Ring
import kotlinx.coroutines.launch

/** Picks the habit a [HabitWidget] shows, when it's placed and when it's reconfigured. */
class HabitWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Backing out without picking cancels placement.
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val app = dotApp
        setContent {
            val settings by app.settings.settings.let { flow ->
                produceState(AppSettings(), flow) { flow.collect { value = it } }
            }
            val snapshot by produceState(TodaySnapshot.Empty) { value = app.repository.currentSnapshot() }
            val scope = rememberCoroutineScope()
            DotTheme(highlight = Color(settings.highlight)) {
                Surface(Modifier.fillMaxSize(), color = Palette.Black) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
                    Text("PICK A HABIT", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 20.dp))
                    if (snapshot.habits.isEmpty() && snapshot !== TodaySnapshot.Empty) {
                        Text("No habits yet. Add one in Dot Habits first.", color = Palette.Muted)
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(snapshot.habits, key = { it.habit.id }) { t ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(widgetId)
                                            updateAppWidgetState(app, glanceId) { it[HabitWidget.HABIT_ID] = t.habit.id }
                                            HabitWidget().update(app, glanceId)
                                            setResult(RESULT_OK, result)
                                            finish()
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val done = t.status == TodayStatus.DONE
                                Ring(t.fraction, t.segments, Modifier.size(48.dp), dashedTrack = t.habit.isNegative, filled = done) {
                                    DotIcon(t.habit.icon, Modifier.size(22.dp), if (done) Palette.Black else Palette.Text)
                                }
                                Spacer(Modifier.width(16.dp))
                                Text(t.habit.name.uppercase(), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
