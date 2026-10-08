package com.madebygps.dothabits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.DayStatus
import com.madebygps.dothabits.domain.HabitRules
import com.madebygps.dothabits.domain.StreakUnit

/** Per-habit streaks, 30-day completion and a 14-day strip — all computed from the same history as Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val raw = ui.raw ?: return
    val today = ui.snapshot.date
    val histories = remember(raw, today) { vm.repository.histories(raw) }
    val highlight = LocalHighlight.current
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = { TextButton(onClick = onBack) { Text("BACK") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (histories.isEmpty()) item { Text("No habits yet.", color = Palette.Muted) }
            items(histories, key = { it.habit.id }) { h ->
                val s = HabitRules.streaks(h.habit, today, raw.settings.weekStart, h.values)
                val rate = HabitRules.completionRate(h.habit, today, raw.settings.weekStart, h.values)
                val unit = if (s.unit == StreakUnit.WEEKS) "WK" else "D"
                Column(Modifier.fillMaxWidth().clickable { onOpen(h.habit.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotIcon(h.habit.icon, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(h.habit.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(rate?.let { "${(it * 100).toInt()}% · 30D" } ?: "--", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("CURRENT ${s.current}$unit", style = MaterialTheme.typography.labelMedium, color = highlight)
                        Text("BEST ${s.best}$unit", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 13 downTo 0) {
                            val d = today.minusDays(i.toLong())
                            val st = HabitRules.dayStatus(h.habit, d, today, h.values)
                            Canvas(Modifier.size(14.dp)) {
                                val r = size.minDimension / 2
                                when (st) {
                                    DayStatus.MET -> drawCircle(highlight, r)
                                    DayStatus.PARTIAL, DayStatus.PENDING -> drawCircle(highlight, r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                                    DayStatus.MISSED -> drawCircle(Palette.Dim, r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                                    else -> drawCircle(Palette.Track, r / 3)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
