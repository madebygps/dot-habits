package com.madebygps.dothabits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.RangeStats
import com.madebygps.dothabits.domain.Stats
import com.madebygps.dothabits.domain.StatsBucket
import com.madebygps.dothabits.domain.StatsRange
import com.madebygps.dothabits.domain.Tally
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel) {
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
                    TextButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.semantics { contentDescription = "How statistics are counted" },
                    ) { Text("?") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(inner),
        ) {
            val sectionGap = (maxHeight * 0.08f).coerceIn(24.dp, 64.dp)
            val chartHeight = (maxHeight * 0.28f).coerceIn(160.dp, 240.dp)
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(sectionGap),
            ) {
                item { RangePicker(range) { range = it } }
                if (!stats.hasHabits) {
                    item { Text("No habits yet.", color = Palette.Muted) }
                } else {
                    item { Overview(stats, range) }
                    if (stats.overall.total > 0) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                HorizontalDivider(color = Palette.Dim)
                                Text("COMPLETION TREND", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                                TrendChart(stats.buckets, stats.monthlyBuckets, chartHeight)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (showHelp) HelpDialog { showHelp = false }
}

@Composable
private fun RangePicker(selected: StatsRange, onPick: (StatsRange) -> Unit) {
    val highlight = LocalHighlight.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRange.entries.forEach { range ->
            val on = range == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .padding(vertical = 2.dp)
                    .background(if (on) highlight else Color.Transparent, RoundedCornerShape(24.dp))
                    .border(1.dp, if (on) highlight else Palette.Dim, RoundedCornerShape(24.dp))
                    .clickable { onPick(range) }
                    .semantics { contentDescription = "Range ${range.label}"; this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(range.label, Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, color = if (on) Color.Black else Palette.Text)
            }
        }
    }
}

@Composable
private fun Overview(stats: RangeStats, range: StatsRange) {
    Column {
        Text("OVERALL COMPLETION", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        Text(
            pct(stats.overall), fontSize = 64.sp, fontWeight = FontWeight.Light,
            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${stats.overall.met} of ${stats.overall.total} goals met",
            style = MaterialTheme.typography.titleMedium, color = Palette.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${stats.from.format(shortDate)} – ${stats.to.format(shortDate)}",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
        )
        val previous = stats.previous?.rate
        val current = stats.overall.rate
        if (previous != null && current != null) {
            val delta = ((current - previous) * 100).roundToInt()
            val changeColor = when {
                delta > 0 -> Color(0xFF75D99A)
                delta < 0 -> Color(0xFFFF8585)
                else -> Palette.Text
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.background(
                    if (delta == 0) LocalDotColors.current.track else changeColor.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp),
                )
                    .border(1.dp, if (delta == 0) Palette.Dim else changeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "${if (delta > 0) "+" else ""}$delta points vs previous ${range.label.lowercase()}",
                    modifier = Modifier.semantics {
                        contentDescription = "$delta percentage points compared with the previous ${range.label.lowercase()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor,
                )
            }
        }
        if (stats.overall.total == 0) {
            Spacer(Modifier.height(12.dp))
            Text("No measured, finished goals in this range.", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        }
        if (stats.missingStepGoals > 0) {
            Spacer(Modifier.height(12.dp))
            Text("NO STEP DATA · ${stats.missingStepGoals} goals excluded", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        }
    }
}

@Composable
private fun TrendChart(buckets: List<StatsBucket>, monthly: Boolean, height: Dp) {
    val highlight = LocalHighlight.current
    val colors = LocalDotColors.current
    Column {
        Canvas(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription = "Completion per ${if (monthly) "month" else "week"}. " +
                buckets.joinToString("; ") { "${it.start}: ${pct(it.tally)}, ${it.tally.met} of ${it.tally.total} measured goals met" }
        }) {
            val slot = size.width / buckets.size.coerceAtLeast(1)
            val bar = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            for (guide in listOf(0.5f, 1f)) {
                val y = size.height * (1 - guide)
                drawLine(colors.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            buckets.forEachIndexed { index, bucket ->
                val x = index * slot + (slot - bar) / 2
                val radius = CornerRadius(bar / 2, bar / 2)
                drawRoundRect(colors.track, Offset(x, 0f), Size(bar, size.height), radius)
                val rate = bucket.tally.rate ?: return@forEachIndexed
                val height = size.height * rate
                if (height > 0) {
                    drawRoundRect(highlight, Offset(x, size.height - height), Size(bar, height), radius)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            val format = if (monthly) DateTimeFormatter.ofPattern("MMM yy") else shortDate
            Text(buckets.firstOrNull()?.start?.format(format).orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            Text(
                if (monthly) "PER MONTH" else "PER WEEK", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Text, textAlign = TextAlign.Center,
            )
            Text(
                buckets.lastOrNull()?.start?.format(format).orEmpty(), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Text, textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How it's counted") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("The selected range applies to the completion rate, comparison and trend.", style = MaterialTheme.typography.bodyMedium)
                Text("Each past scheduled day is one goal. Each finished week is one goal for weekly habits. Rest days, today and the current week don't count.", style = MaterialTheme.typography.bodyMedium)
                Text("Completion is goals met divided by measured goals. Missing step goals are excluded, never treated as zero.", style = MaterialTheme.typography.bodyMedium)
                Text("The comparison chip shows percentage points, not relative percent change. For example, 88% now versus 77% before is +11 points. It compares with the equally long period just before. All time has no comparison. Changes in your mix of habits can affect the overall rate.", style = MaterialTheme.typography.bodyMedium)
                Text("Bars group the selected range by week, or month for long ranges. Edge bars can cover only part of a week or month.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

private fun pct(tally: Tally) = tally.rate?.let { "${(it * 100).roundToInt()}%" } ?: "--"
