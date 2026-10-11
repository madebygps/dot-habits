package com.madebygps.dothabits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.HabitGuide
import com.madebygps.dothabits.domain.HabitStats
import com.madebygps.dothabits.domain.RangeStats
import com.madebygps.dothabits.domain.Stats
import com.madebygps.dothabits.domain.StatsRange
import com.madebygps.dothabits.domain.Tally
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val raw = ui.raw ?: return
    val today = ui.snapshot.date
    var range by rememberSaveable { mutableStateOf(StatsRange.D30) }
    var tab by rememberSaveable { mutableStateOf(InsightTab.CHANGES) }
    var showHelp by remember { mutableStateOf(false) }
    val stats = remember(raw, today, range) {
        Stats.compute(vm.repository.histories(raw), today, raw.settings.weekStart, range)
    }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { DotScreenTitle("Insights") },
                actions = {
                    TextButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.semantics { contentDescription = "How insights are counted" },
                    ) { Text("?") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RangePicker(range) { range = it }
            if (!stats.hasHabits) {
                Text("Add habits to see patterns across them.", color = Palette.Muted)
            } else {
                Overview(stats)
                SecondaryTabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = Palette.Black,
                    contentColor = Palette.Text,
                ) {
                    InsightTab.entries.forEach { option ->
                        Tab(
                            selected = tab == option,
                            onClick = { tab = option },
                            selectedContentColor = Palette.Text,
                            unselectedContentColor = Palette.Muted,
                            text = {
                                Text(option.label, fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall)
                            },
                        )
                    }
                }
                key(tab) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item {
                            when (tab) {
                                InsightTab.CHANGES -> Dashboard(stats)
                                InsightTab.MISSED -> MissedGoals(stats)
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
    if (showHelp) HelpDialog(stats) { showHelp = false }
}

private enum class InsightTab(val label: String) {
    CHANGES("Dashboard"), MISSED("Missed goals"),
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
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center, color = if (on) Palette.Black else Palette.Text)
            }
        }
    }
}

@Composable
private fun Overview(stats: RangeStats) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${stats.from.format(shortDate)} – ${stats.to.format(shortDate)}",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        Text(stats.overall.percentage, fontSize = 64.sp, fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace)
        Text("${stats.overall.met} of ${stats.overall.total} goals met",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        if (stats.overall.total == 0) {
            Text("No measured, finished goals in this range.", color = Palette.Muted, textAlign = TextAlign.Center)
        }
        if (stats.missingStepGoals > 0) {
            Text("No step data · ${stats.missingStepGoals} goals excluded",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Section(subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        content()
    }
}

@Composable
private fun Dashboard(stats: RangeStats) {
    val habits = stats.habits.sortedWith(compareBy<HabitStats> { it.habit.position }.thenBy { it.habit.id })
    Column(Modifier.fillMaxWidth().border(1.dp, Palette.Line)) {
        habits.chunked(2).forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = Palette.Line)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                DashboardCell(row[0], Modifier.weight(1f))
                VerticalDivider(Modifier.fillMaxHeight(), color = Palette.Line)
                if (row.size == 2) DashboardCell(row[1], Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HabitHeading(stats: HabitStats) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DotIcon(stats.habit.icon, Modifier.size(24.dp))
        Text(stats.habit.name, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun changeColor(improving: Boolean): Color {
    val dark = LocalDotColors.current.background == Color.Black
    return if (improving) {
        if (dark) Color(0xFF81C784) else Color(0xFF256B29)
    } else {
        if (dark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
    }
}

@Composable
private fun DashboardCell(stats: HabitStats, modifier: Modifier) {
    val delta = stats.changePoints
    val color = when {
        delta == null || delta == 0 -> Palette.Text
        else -> changeColor(delta > 0)
    }
    val direction = when {
        delta == null -> "No comparison history"
        delta > 0 -> "Improving"
        delta < 0 -> "Declining"
        else -> "Unchanged at whole percentage-point precision"
    }
    Column(
        modifier.heightIn(min = 96.dp).padding(horizontal = 12.dp, vertical = 12.dp).semantics(mergeDescendants = true) {
            contentDescription = "${stats.habit.name}, ${stats.current.percentage} completion. $direction. " +
                "${stats.current.met} of ${stats.current.total} measured goals met. " +
                "Previous rate: ${stats.previous?.percentage ?: "--"}"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotIcon(stats.habit.icon, Modifier.size(28.dp))
        val marker = when {
            delta == null -> ""
            delta > 0 -> "↑ "
            delta < 0 -> "↓ "
            else -> "– "
        }
        Text("$marker${stats.current.percentage}", color = color,
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge)
        Text(stats.habit.name, style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RateBar(rate: Float?) {
    if (rate == null) {
        Spacer(Modifier.height(6.dp))
        return
    }
    Box(Modifier.fillMaxWidth().height(6.dp).background(Palette.Track, RoundedCornerShape(3.dp))) {
        if (rate > 0f) {
            Box(Modifier.fillMaxWidth(rate).height(6.dp).background(LocalHighlight.current, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun MissedGoals(stats: RangeStats) {
    val totalMissed = stats.overall.missed
    Section("$totalMissed missed goals across all habits") {
        if (totalMissed == 0) {
            Text(if (stats.overall.total == 0) "No measured goals to assess yet."
                else "All measured goals were met.", color = Palette.Muted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                stats.missedGoals.forEach { habit ->
                    val share = Tally(habit.current.missed, totalMissed)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HabitHeading(habit)
                        Text("${habit.current.missed} missed · ${share.percentage} of all misses",
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        RateBar(share.rate)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpDialog(stats: RangeStats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How it's counted") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Selected: ${stats.from.format(shortDate)} – ${stats.to.format(shortDate)}")
                Text("Compared with: ${stats.previousFrom.format(shortDate)} – ${stats.previousTo.format(shortDate)}")
                val unmeasured = stats.habits.count { it.changePoints == null }
                if (unmeasured > 0) {
                    Text("$unmeasured ${if (unmeasured == 1) "habit doesn't" else "habits don't"} have measured goals in both date ranges to compare.")
                }
                Text("MEASURED GOALS", style = MaterialTheme.typography.labelMedium)
                stats.habits.forEach { habit ->
                    Text("${habit.habit.name}: ${habit.previous?.met}/${habit.previous?.total} before; " +
                        "${habit.current.met}/${habit.current.total} now (${habit.current.percentage} met)",
                        style = MaterialTheme.typography.bodySmall)
                }
                HabitGuide.statisticsHelp.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d, yyyy")
