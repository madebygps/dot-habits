package com.madebygps.dothabits.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.DayStatus
import com.madebygps.dothabits.domain.HabitHistory
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.HabitRules
import com.madebygps.dothabits.domain.HabitToday
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
fun DetailScreen(vm: MainViewModel, habitId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val raw = ui.raw ?: return
    val today = ui.snapshot.habits.firstOrNull { it.habit.id == habitId }
    if (today == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val history = remember(raw, ui.snapshot) { vm.repository.histories(raw).first { it.habit.id == habitId } }
    val notes by vm.repository.notes(habitId).collectAsStateWithLifecycle(emptyMap())
    val weekStart = raw.settings.weekStart
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { Text(today.habit.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("BACK") } },
                actions = { TextButton(onClick = { onEdit(habitId) }) { Text("EDIT") } },
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
            item { StatsRow(today, history, ui.snapshot.date, weekStart) }
            item { HistoryCalendar(history, ui.snapshot.date, weekStart, onDay = { editing = it }) }
            item {
                OutlinedButton(onClick = { editing = ui.snapshot.date }, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit today / add note")
                }
            }
            if (today.habit.type == HabitType.TIMED && history.sessions.isNotEmpty()) {
                item { Text("SESSIONS", style = MaterialTheme.typography.labelSmall) }
                history.sessions.sortedByDescending { it.start }.take(10).forEach { s ->
                    item(key = "s-${s.id}") { SessionRow(s, onDelete = { vm.viewModelScopeLaunch { vm.repository.deleteSession(s.id) } }) }
                }
            }
            if (notes.isNotEmpty()) {
                item { Text("NOTES", style = MaterialTheme.typography.labelSmall) }
                notes.entries.sortedByDescending { it.key }.take(30).forEach { (d, text) ->
                    item(key = "n-$d") {
                        Column(Modifier.fillMaxWidth().clickable { editing = d }) {
                            Text(d.format(dayFmt).uppercase(), style = MaterialTheme.typography.labelSmall)
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    editing?.let { day ->
        EditDayDialog(vm, today, history, day, notes[day].orEmpty(), onDismiss = { editing = null })
    }
}

@Composable
private fun Hero(t: HabitToday, vm: MainViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp)) {
            Ring(t.fraction, t.segments, Modifier.fillMaxSize(), dashedTrack = t.habit.isNegative) {
                DotIcon(t.habit.icon, Modifier.size(72.dp))
            }
            if (t.habit.type == HabitType.TIMED) {
                PlayPauseButton(t.timerRunning, 54.dp, Modifier.align(Alignment.BottomEnd)) { vm.toggleTimer(t.habit.id, t.timerRunning) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (t.habit.type == HabitType.TIMED) {
            val s = t.value
            DotText("%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60), dot = 5.dp)
            Spacer(Modifier.height(8.dp))
        }
        Text(HabitLabels.detail(t).ifEmpty { t.status.name.replace('_', ' ') }, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(scheduleText(t), style = MaterialTheme.typography.labelSmall)
        if (t.habit.type == HabitType.STEPS && !t.hasData) {
            Text(
                "No Health Connect step data for today yet. Check Settings › Health Connect.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun scheduleText(t: HabitToday): String {
    val h = t.habit
    val target = when (h.type) {
        HabitType.TIMED -> TimerMath.formatDuration(h.dailyGoalUnits)
        HabitType.STEPS -> "${h.dailyTarget} steps"
        HabitType.COUNT -> if (h.isNegative) "≤ ${h.dailyTarget} per day" else "${h.dailyTarget}× per day"
    }
    val sched = when (h.schedule.kind) {
        com.madebygps.dothabits.domain.ScheduleKind.DAILY -> "every day"
        com.madebygps.dothabits.domain.ScheduleKind.WEEKDAYS -> h.schedule.weekdays.sorted().joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        com.madebygps.dothabits.domain.ScheduleKind.DAYS_PER_WEEK -> "${h.schedule.perWeek} different days a week"
        com.madebygps.dothabits.domain.ScheduleKind.TIMES_PER_WEEK -> "${h.schedule.perWeek} times a week"
    }
    return if (h.schedule.kind == com.madebygps.dothabits.domain.ScheduleKind.TIMES_PER_WEEK) sched.uppercase()
    else "$target · $sched".uppercase()
}

@Composable
private fun StatsRow(t: HabitToday, h: HabitHistory, today: LocalDate, weekStart: DayOfWeek) {
    val unit = if (t.streak.unit == StreakUnit.WEEKS) "WK" else "D"
    val rate = HabitRules.completionRate(h.habit, today, weekStart, h.values)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("CURRENT", "${t.streak.current}$unit")
        Stat("BEST", "${t.streak.best}$unit")
        Stat("DAYS MET", HabitRules.daysMet(h.habit, today, h.values).toString())
        Stat("30D", rate?.let { "${(it * 100).toInt()}%" } ?: "--")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DotText(value, dot = 4.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HistoryCalendar(h: HabitHistory, today: LocalDate, weekStart: DayOfWeek, onDay: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val highlight = LocalHighlight.current
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
                            val status = HabitRules.dayStatus(h.habit, date, today, h.values)
                            val clickable = !date.isAfter(today)
                            Box(
                                Modifier.size(34.dp).let { if (clickable) it.clickable(onClickLabel = date.format(dayFmt)) { onDay(date) } else it },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(26.dp)) {
                                    val rad = size.minDimension / 2
                                    when (status) {
                                        DayStatus.MET -> drawCircle(highlight, rad)
                                        DayStatus.PARTIAL -> drawCircle(highlight, rad - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
                                        DayStatus.MISSED -> drawCircle(Palette.Dim, rad - 2.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                                        DayStatus.PENDING -> drawCircle(Palette.Text, rad - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
                                        else -> Unit
                                    }
                                }
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (status) {
                                        DayStatus.MET -> androidx.compose.ui.graphics.Color.Black
                                        DayStatus.FUTURE, DayStatus.BEFORE_START, DayStatus.REST -> Palette.Dim
                                        else -> Palette.Text
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("● met   ○ partial / today   ◌ missed   grey = rest or before start", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SessionRow(s: TimerSession, onDelete: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val end = TimerMath.effectiveEnd(s, Instant.now())
    val secs = java.time.Duration.between(s.start, end).seconds.coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                s.start.atZone(zone).format(dayFmt).uppercase() + "  " + s.start.atZone(zone).format(timeFmt) +
                    when (s.state) {
                        SessionState.RUNNING -> " → now"
                        SessionState.NEEDS_REVIEW -> " → ? (review)"
                        SessionState.CLOSED -> " → " + end.atZone(zone).format(timeFmt)
                    },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(TimerMath.formatDuration(secs), style = MaterialTheme.typography.labelSmall)
        }
        if (s.state != SessionState.RUNNING) TextButton(onClick = onDelete) { Text("DELETE") }
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
            "Started ${s.start.atZone(zone).format(timeFmt)}. Last confirmed running at ${s.lastAlive.atZone(zone).format(timeFmt)}. " +
                "Only time up to the last confirmation is counted until you choose.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    existingNote: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var manual by remember { mutableLongStateOf(-1L) }
    var note by remember { mutableStateOf(existingNote) }
    LaunchedEffect(day) { manual = vm.repository.manualAmount(t.habit.id, day) }
    val habit = t.habit
    val zone = ZoneId.systemDefault()
    val sessionSecs = TimerMath.secondsOnDay(h.sessions, day, zone, Instant.now())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.format(dayFmt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    manual < 0 -> Text("…")
                    habit.type == HabitType.STEPS -> Text(
                        "Steps come from Health Connect and can't be edited here: ${h.values[day] ?: "no data"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    habit.type == HabitType.TIMED -> {
                        Text("Timer sessions: ${TimerMath.formatDuration(sessionSecs)}", style = MaterialTheme.typography.bodyMedium)
                        Stepper("Manual minutes", manual / 60, step = 5, min = 0, max = 24 * 60) { manual = it * 60 }
                    }
                    else -> Stepper(if (habit.isNegative) "Slips" else "Completions", manual, step = 1, min = 0, max = 99) { manual = it }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (manual >= 0 && habit.type != HabitType.STEPS) vm.repository.setManualAmount(habit.id, day, manual)
                    vm.repository.setNote(habit.id, day, note)
                    onDismiss()
                }
            }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
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
