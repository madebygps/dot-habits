package com.madebygps.dothabits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.HabitRangeStats
import com.madebygps.dothabits.domain.RangeStats
import com.madebygps.dothabits.domain.Stats
import com.madebygps.dothabits.domain.StatsBucket
import com.madebygps.dothabits.domain.StatsRange
import com.madebygps.dothabits.domain.StreakUnit
import com.madebygps.dothabits.domain.Tally
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Overall completion for a chosen range, its trend, weekday consistency and a compact row per habit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val raw = ui.raw ?: return
    val today = ui.snapshot.date
    var range by rememberSaveable { mutableStateOf(StatsRange.D30) }
    var showHelp by remember { mutableStateOf(false) }
    val stats = remember(raw, today, range) {
        Stats.compute(vm.repository.histories(raw), today, raw.settings.weekStart, range)
    }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { DotScreenTitle("Statistics") },
                actions = {
                    TextButton(onClick = { showHelp = true }, modifier = Modifier.semantics { contentDescription = "How statistics are counted" }) { Text("?") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item { RangePicker(range) { range = it } }
            if (stats.habits.isEmpty()) {
                item { Text("No habits yet.", color = Palette.Muted) }
            } else {
                item { Overview(stats, range) }
                item { Weekdays(stats, raw.settings.weekStart) }
                item { Text("HABITS", style = MaterialTheme.typography.labelSmall) }
                items(stats.habits, key = { it.habit.id }) { HabitRow(it) { onOpen(it.habit.id) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showHelp) HelpDialog { showHelp = false }
}

@Composable
private fun RangePicker(selected: StatsRange, onPick: (StatsRange) -> Unit) {
    val highlight = LocalHighlight.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRange.entries.forEach { r ->
            val on = r == selected
            Box(
                Modifier.weight(1f)
                    .height(36.dp)
                    .background(if (on) highlight else Color.Transparent, RoundedCornerShape(18.dp))
                    .border(1.dp, if (on) highlight else Palette.Dim, RoundedCornerShape(18.dp))
                    .clickable { onPick(r) }
                    .semantics { contentDescription = "Range ${r.label}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(r.label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.Black else Palette.Text)
            }
        }
    }
}

@Composable
private fun Overview(s: RangeStats, range: StatsRange) {
    val highlight = LocalHighlight.current
    Column {
        Text("COMPLETION", style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                pct(s.overall),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
            )
            Spacer(Modifier.width(12.dp))
            val prev = s.previous?.rate
            val now = s.overall.rate
            if (prev != null && now != null) {
                val delta = ((now - prev) * 100).roundToInt()
                val text = when {
                    delta > 0 -> "▲ $delta%"
                    delta < 0 -> "▼ ${-delta}%"
                    else -> "= 0%"
                }
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(text, style = MaterialTheme.typography.labelLarge, color = if (delta >= 0) highlight else Palette.Muted)
                    Text("VS PREV ${range.label}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            "${s.overall.met} of ${s.overall.total} goals met · ${s.from.format(shortDate)} – ${s.to.format(shortDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(16.dp))
        TrendChart(s.buckets, s.monthlyBuckets)
    }
}

@Composable
private fun TrendChart(buckets: List<StatsBucket>, monthly: Boolean) {
    val highlight = LocalHighlight.current
    val colors = LocalDotColors.current
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Completion per ${if (monthly) "month" else "week"}" }) {
            val n = buckets.size.coerceAtLeast(1)
            val slot = size.width / n
            val bar = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            // 50% and 100% guides.
            for (g in listOf(0.5f, 1f)) {
                val y = size.height * (1 - g)
                drawLine(colors.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            buckets.forEachIndexed { i, b ->
                val x = i * slot + (slot - bar) / 2
                val r = CornerRadius(bar / 2, bar / 2)
                drawRoundRect(colors.track, Offset(x, 0f), Size(bar, size.height), r)
                val rate = b.tally.rate ?: return@forEachIndexed
                val h = (size.height * rate).coerceAtLeast(if (rate > 0) bar else 0f)
                if (h > 0) drawRoundRect(highlight, Offset(x, size.height - h), Size(bar, h), r)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            val fmt = if (monthly) DateTimeFormatter.ofPattern("MMM yy") else shortDate
            Text(buckets.firstOrNull()?.start?.format(fmt)?.uppercase() ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text(
                if (monthly) "PER MONTH" else "PER WEEK",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(buckets.lastOrNull()?.start?.format(fmt)?.uppercase() ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Weekdays(s: RangeStats, firstDay: DayOfWeek) {
    val highlight = LocalHighlight.current
    val colors = LocalDotColors.current
    val days = (0L until 7L).map { firstDay.plus(it) }
    val rates = days.map { s.weekdays[it]?.rate }
    val locale = LocalConfiguration.current.locales[0]
    val known = rates.filterNotNull()
    if (known.isEmpty()) return
    val best = known.max()
    val worst = known.min()
    Column {
        Text("BY WEEKDAY", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                val rate = rates[i]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rate?.let { "${(it * 100).roundToInt()}" } ?: "–", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Canvas(Modifier.width(18.dp).height(56.dp)) {
                        val r = CornerRadius(size.width / 2, size.width / 2)
                        drawRoundRect(colors.track, cornerRadius = r)
                        if (rate != null && rate > 0) {
                            val h = (size.height * rate).coerceAtLeast(size.width)
                            val color = if (best != worst && rate == best) highlight else if (rate == worst && best != worst) colors.dim else colors.muted
                            drawRoundRect(color, Offset(0f, size.height - h), Size(size.width, h), r)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(d.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HabitRow(h: HabitRangeStats, onClick: () -> Unit) {
    val highlight = LocalHighlight.current
    val colors = LocalDotColors.current
    val unit = if (h.streaks.unit == StreakUnit.WEEKS) "W" else "D"
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotIcon(h.habit.icon, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(h.habit.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(pct(h.tally), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(colors.track, cornerRadius = r)
            val rate = h.tally.rate ?: 0f
            if (rate > 0) drawRoundRect(highlight, size = Size((size.width * rate).coerceAtLeast(size.height), size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "STREAK ${h.streaks.current}$unit · BEST ${h.streaks.best}$unit",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How it's counted") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Completion is the share of goals met in the range.", style = MaterialTheme.typography.bodyMedium)
                Text("Daily and selected-weekday habits: each scheduled day is one goal. Rest days don't count.", style = MaterialTheme.typography.bodyMedium)
                Text("Days-per-week and times-per-week habits: each finished week is one goal.", style = MaterialTheme.typography.bodyMedium)
                Text("Only finished days and weeks count, so today never lowers the numbers. ▲/▼ compares with the period just before.", style = MaterialTheme.typography.bodyMedium)
                Text("By weekday uses daily and selected-weekday habits.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

private fun pct(t: Tally) = t.rate?.let { "${(it * 100).roundToInt()}%" } ?: "--"
