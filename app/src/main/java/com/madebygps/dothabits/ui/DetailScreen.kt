package com.madebygps.dothabits.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.DayStatus
import com.madebygps.dothabits.domain.DetailPresentation
import com.madebygps.dothabits.domain.HabitGuide
import com.madebygps.dothabits.domain.HabitHistory
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.HabitRules
import com.madebygps.dothabits.domain.HabitToday
import com.madebygps.dothabits.domain.HabitTileState
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.StreakUnit
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.domain.TimerSession
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: MainViewModel, habitId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onGuide: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val raw = ui.raw ?: return
    val today = ui.snapshot.habits.firstOrNull { it.habit.id == habitId }
    if (today == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val history = remember(raw, ui.snapshot) { vm.repository.histories(raw).first { it.habit.id == habitId } }
    val weekStart = raw.settings.weekStart
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var calendarExpanded by remember(habitId) { mutableStateOf(false) }
    var statisticsExpanded by remember(habitId) { mutableStateOf(false) }
    var selectedDay by remember(habitId) { mutableStateOf(ui.snapshot.date) }
    var activityDay by remember(habitId) { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { Text(today.habit.name) },
                actions = {
                    TextButton(onClick = { showHelp = true }, modifier = Modifier.semantics { contentDescription = "How to read this screen" }) { Text("?") }
                    TextButton(onClick = { onEdit(habitId) }) { Text("EDIT") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Hero(today, vm) }
            history.sessions.filter { it.state == SessionState.NEEDS_REVIEW }.forEach { s ->
                item(key = "review-${s.id}") { ReviewCard(s) { end -> vm.viewModelScopeLaunch { vm.repository.resolveReview(s.id, end) } } }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val unit = if (today.streak.unit == StreakUnit.WEEKS) "WK" else "D"
                    val fullUnit = if (today.streak.unit == StreakUnit.WEEKS) "weeks" else "days"
                    Stat("CURRENT", "${today.streak.current}$unit", "${today.streak.current} $fullUnit")
                    Stat("BEST", "${today.streak.best}$unit", "${today.streak.best} $fullUnit")
                }
            }
            item { RecentChain(history, ui.snapshot.date, weekStart) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionToggle("CALENDAR", calendarExpanded) { calendarExpanded = !calendarExpanded }
                    if (calendarExpanded) {
                        HistoryCalendar(history, ui.snapshot.date, weekStart, selectedDay, onDay = {
                            selectedDay = it
                            activityDay = it
                        })
                    }
                }
            }
            item { SectionToggle("STATISTICS", statisticsExpanded) { statisticsExpanded = !statisticsExpanded } }
            if (statisticsExpanded) {
                item {
                    val rate = HabitRules.completionRate(history.habit, ui.snapshot.date, weekStart, history.values)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("DAYS MET", DetailPresentation.closedDaysMet(history, ui.snapshot.date).toString())
                        Stat("LAST 30D", rate?.let { "${(it * 100).toInt()}%" } ?: "--")
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    activityDay?.let { day ->
        ModalBottomSheet(
            onDismissRequest = { activityDay = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.Black,
            contentColor = Palette.Text,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            ) {
                DateActivity(
                    history, day, ui.snapshot.date,
                    onEdit = { editing = day },
                )
            }
        }
    }
    editing?.let { day ->
        EditDayDialog(vm, today, history, day, onDismiss = { editing = null })
    }
    if (showHelp) GuideHelpDialog(HabitGuide.contextual(today.habit), onDismiss = { showHelp = false }, onGuide = onGuide)
}

@Composable
private fun Hero(t: HabitToday, vm: MainViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp)) {
            val tile = HabitTileState.from(t)
            HabitTile(tile, Modifier.fillMaxSize().semantics { contentDescription = HabitLabels.accessibility(t) }) {
                DotIcon(t.habit.icon, Modifier.size(60.dp), when {
                    tile.solid -> Palette.Black
                    tile.dimmed -> Palette.Dim
                    else -> Palette.Text
                })
            }
            if (t.habit.type == HabitType.TIMED) {
                PlayPauseButton(t.timerRunning, 48.dp, Modifier.align(Alignment.BottomEnd)) { vm.toggleTimer(t.habit.id) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (t.habit.type == HabitType.TIMED) {
            val timer = DetailPresentation.timerContext(t)
            Text(timer.primary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(timer.secondary, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.Center)
        } else {
            HabitLabels.detail(t).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            Text(DetailPresentation.schedule(t.habit), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, spokenValue: String = value) {
    Column(Modifier.clearAndSetSemantics { contentDescription = "$label: $spokenValue" }, horizontalAlignment = Alignment.CenterHorizontally) {
        DotText(value, dot = 3.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionToggle(label: String, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) {
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Start, style = MaterialTheme.typography.labelMedium)
        Text(if (expanded) "−" else "+")
    }
}

@Composable
private fun RecentChain(h: HabitHistory, today: LocalDate, weekStart: DayOfWeek) {
    val points = DetailPresentation.recentChain(h, today, weekStart)
    val highlight = LocalHighlight.current
    val colors = LocalDotColors.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (h.habit.schedule.isWeekly) "RECENT WEEKS" else "RECENT DAYS", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Box(Modifier.widthIn(max = 300.dp).fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                drawLine(colors.dim, Offset(size.width / 14, size.height / 2), Offset(size.width * 13 / 14, size.height / 2), 1.dp.toPx())
            }
            Row(Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    val label = if (h.habit.schedule.isWeekly) point.start.format(DateTimeFormatter.ofPattern("d/M"))
                        else point.start.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                    Column(
                        Modifier.weight(1f).clearAndSetSemantics {
                            contentDescription = (if (h.habit.schedule.isWeekly) "Week ${point.start} to ${point.end}" else point.start.format(dayFmt)) +
                                ". ${point.stateLabel}" + if (point.current) ". Current period" else ""
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(Modifier.size(28.dp)) {
                            drawCircle(colors.background, 9.dp.toPx())
                            val radius = 6.dp.toPx()
                            when {
                                point.missingSteps -> drawCircle(colors.dim, radius, style = Stroke(1.dp.toPx()))
                                point.status == DayStatus.MET -> drawCircle(highlight, radius)
                                point.status == DayStatus.PARTIAL -> drawCircle(highlight, radius, style = Stroke(2.dp.toPx()))
                                point.status == DayStatus.PENDING -> drawCircle(colors.text, radius, style = Stroke(1.dp.toPx()))
                                point.status == DayStatus.MISSED -> drawCircle(colors.dim, radius, style = Stroke(1.5.dp.toPx()))
                                point.status == DayStatus.REST -> drawCircle(colors.dim, 3.dp.toPx())
                                else -> drawCircle(colors.dim, 2.dp.toPx())
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        Box(Modifier.height(8.dp), contentAlignment = Alignment.Center) {
                            if (point.current) Box(Modifier.size(3.dp).clip(CircleShape).background(Palette.Text))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCalendar(h: HabitHistory, today: LocalDate, weekStart: DayOfWeek, selectedDay: LocalDate, onDay: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val highlight = LocalHighlight.current
    val colors = LocalDotColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase() + " " + month.year,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) { Text("›") }
        }
        val days = (0 until 7).map { weekStart.plus(it.toLong()) }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val first = month.atDay(1)
        val lead = ((first.dayOfWeek.value - weekStart.value) + 7) % 7
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (idx in 0 until month.lengthOfMonth()) {
                            val date = month.atDay(idx + 1)
                            val point = DetailPresentation.dayPoint(h, date, today)
                            val status = point.status
                            val clickable = !date.isAfter(today)
                            Box(
                                Modifier.size(42.dp).semantics {
                                    contentDescription = "${date.format(dayFmt)}. ${point.stateLabel}" +
                                        if (point.current) ". Today" else ""
                                    stateDescription = if (date == selectedDay) "Selected" else "Not selected"
                                }.let { if (clickable) it.clickable(onClickLabel = "Inspect date") { onDay(date) } else it },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(34.dp)) {
                                    val selected = date == selectedDay
                                    if (!point.missingSteps && (!selected || status == DayStatus.MET)) {
                                        drawDayMark(status, highlight, colors)
                                    }
                                    if (selected) {
                                        val inset = 0.5.dp.toPx()
                                        drawRoundRect(
                                            if (status == DayStatus.PARTIAL && !point.missingSteps) highlight else colors.text,
                                            topLeft = Offset(inset, inset),
                                            size = Size(size.width - 2 * inset, size.height - 2 * inset),
                                            cornerRadius = CornerRadius(size.minDimension * TileGeometry.CORNER_FRACTION),
                                            style = Stroke(1.dp.toPx()),
                                        )
                                    }
                                    if (point.current) drawCircle(
                                        if (status == DayStatus.MET && !point.missingSteps) colors.background else colors.text,
                                        1.5.dp.toPx(),
                                        Offset(size.width / 2, size.height - 3.dp.toPx()),
                                    )
                                }
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        point.missingSteps -> Palette.Muted
                                        status == DayStatus.MET -> androidx.compose.ui.graphics.Color.Black
                                        status in listOf(DayStatus.FUTURE, DayStatus.BEFORE_START, DayStatus.REST) -> Palette.Dim
                                        else -> Palette.Text
                                    },
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateActivity(h: HabitHistory, day: LocalDate, today: LocalDate, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(day.format(dayFmt).uppercase(), style = MaterialTheme.typography.labelMedium)
        Text(
            DetailPresentation.dayProgress(h, day, today),
            style = MaterialTheme.typography.titleLarge,
        )
        if (h.habit.type != HabitType.STEPS) {
            TextButton(onClick = onEdit) {
                Text(if (h.habit.type == HabitType.TIMED) "Adjust completion" else "Edit day")
            }
        }
    }
}

@Composable
private fun ReviewCard(s: TimerSession, onResolve: (Instant?) -> Unit) {
    val zone = ZoneId.systemDefault()
    val bootAt = Instant.now().minusMillis(SystemClock.elapsedRealtime())
    val highlight = LocalHighlight.current
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, highlight, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("TIMER INTERRUPTED BY RESTART", style = MaterialTheme.typography.labelMedium, color = highlight)
        Spacer(Modifier.height(6.dp))
        Text(
            "Started ${s.start.atZone(zone).format(dayFmt)} ${s.start.atZone(zone).format(timeFmt)}. " +
                "Last confirmed running at ${s.lastAlive.atZone(zone).format(dayFmt)} ${s.lastAlive.atZone(zone).format(timeFmt)}. " +
                "Only time up to the last confirmation is counted until you choose.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onResolve(s.lastAlive) }) { Text("Keep to ${s.lastAlive.atZone(zone).format(timeFmt)}") }
            if (bootAt.isAfter(s.lastAlive)) {
                OutlinedButton(onClick = { onResolve(bootAt) }) { Text("To restart ${bootAt.atZone(zone).format(timeFmt)}") }
            }
        }
        TextButton(onClick = { onResolve(null) }) { Text("Discard session") }
    }
}

@Composable
private fun EditDayDialog(
    vm: MainViewModel,
    t: HabitToday,
    h: HabitHistory,
    day: LocalDate,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val habit = t.habit
    val completed = (h.values[day] ?: 0).coerceAtLeast(0) / habit.sessionSeconds.coerceAtLeast(1)
    var amount by remember(day, habit.id) {
        mutableStateOf<Long?>(if (habit.type == HabitType.TIMED) completed else null)
    }
    LaunchedEffect(day, habit.id) {
        if (habit.type == HabitType.COUNT) amount = vm.repository.manualAmount(habit.id, day)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit.type == HabitType.TIMED) "Adjust completion" else day.format(dayFmt)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    habit.type == HabitType.STEPS -> Text(
                        "Steps come from Health Connect and can't be edited here: ${h.values[day] ?: "no data"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    amount == null -> Text("…")
                    habit.type == HabitType.TIMED ->
                        Stepper("Completed sessions", amount ?: 0, step = 1, min = 0, max = maxOf(99L, completed, habit.sessions.toLong())) { amount = it }
                    else -> Stepper(if (habit.isNegative) "Slips" else "Completions", amount ?: 0, step = 1, min = 0, max = 99) { amount = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null || habit.type == HabitType.STEPS, onClick = {
                scope.launch {
                    amount?.let {
                        when (habit.type) {
                            HabitType.TIMED -> vm.repository.setTimerCompletions(habit.id, day, it)
                            HabitType.COUNT -> vm.repository.setManualAmount(habit.id, day, it)
                            HabitType.STEPS -> Unit
                        }
                    }
                    onDismiss()
                }
            }) { Text(if (habit.type == HabitType.STEPS) "CLOSE" else "SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDayMark(
    status: DayStatus,
    highlight: androidx.compose.ui.graphics.Color,
    colors: DotColors,
) {
    val side = size.minDimension
    fun mark(color: androidx.compose.ui.graphics.Color, stroke: Float = 0f) {
        val inset = stroke / 2
        drawRoundRect(
            color,
            topLeft = Offset(inset, inset),
            size = Size(side - stroke, side - stroke),
            cornerRadius = CornerRadius(side * TileGeometry.CORNER_FRACTION),
            style = if (stroke > 0) Stroke(stroke) else androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
    when (status) {
        DayStatus.MET -> mark(highlight)
        DayStatus.PARTIAL -> mark(highlight, 2.dp.toPx())
        DayStatus.MISSED -> mark(colors.dim, 1.5.dp.toPx())
        DayStatus.PENDING -> mark(colors.text, 1.dp.toPx())
        else -> Unit
    }
}

@Composable
fun Stepper(label: String, value: Long, step: Long, min: Long, max: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−") }
        Text("$value", Modifier.width(64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onChange((value + step).coerceAtMost(max)) }, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+") }
    }
}
