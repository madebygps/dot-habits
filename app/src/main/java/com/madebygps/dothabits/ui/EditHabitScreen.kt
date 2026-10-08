package com.madebygps.dothabits.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.domain.DotIcons
import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.Schedule
import com.madebygps.dothabits.domain.ScheduleKind
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.system.Notifications
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditHabitScreen(vm: MainViewModel, habitId: Long, onDone: () -> Unit, onDeleted: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val weekStart = ui.raw?.settings?.weekStart ?: DayOfWeek.MONDAY
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(habitId == 0L) }
    var draft by remember {
        mutableStateOf(Habit(name = "", icon = "check", type = HabitType.COUNT, dailyTarget = 1, createdOn = LocalDate.now()))
    }
    LaunchedEffect(habitId) {
        if (habitId != 0L) vm.repository.habit(habitId)?.let { draft = it }
        loaded = true
    }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (habitId == 0L) "New habit" else "Edit habit") },
                navigationIcon = { TextButton(onClick = onDone) { Text("CANCEL") } },
                actions = {
                    TextButton(
                        enabled = loaded && draft.name.isNotBlank() && isValid(draft),
                        onClick = {
                            scope.launch {
                                runCatching { vm.repository.saveHabit(draft.copy(name = draft.name.trim())) }
                                onDone()
                            }
                        },
                    ) { Text("SAVE") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OutlinedTextField(
                draft.name, { draft = draft.copy(name = it.take(40)) },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Section("ICON")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DotIcons.names.forEach { name ->
                    val selected = draft.icon == name
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) LocalHighlight.current else Palette.Track, CircleShape)
                            .clickable(onClickLabel = name) { draft = draft.copy(icon = name) },
                        contentAlignment = Alignment.Center,
                    ) { DotIcon(name, Modifier.size(26.dp)) }
                }
            }

            Section("TYPE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HabitType.COUNT to "Check / count", HabitType.TIMED to "Timer", HabitType.STEPS to "Steps").forEach { (type, label) ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { draft = withType(draft, type) },
                        label = { Text(label) },
                    )
                }
            }
            if (draft.type == HabitType.COUNT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avoid this habit", style = MaterialTheme.typography.bodyLarge)
                        Text("Hold to log a slip. Days succeed while slips stay within the allowance.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    Switch(draft.isNegative, {
                        draft = draft.copy(
                            isNegative = it,
                            dailyTarget = if (it) 0 else 1,
                            schedule = if (it && draft.schedule.isWeekly) Schedule.Daily else draft.schedule,
                        )
                    })
                }
            }

            Section("GOAL")
            when (draft.type) {
                HabitType.COUNT -> if (draft.isNegative) {
                    Stepper("Allowed slips per day", draft.dailyTarget.toLong(), 1, 0, 20) { draft = draft.copy(dailyTarget = it.toInt()) }
                } else if (draft.schedule.kind != ScheduleKind.TIMES_PER_WEEK) {
                    Stepper("Times per day", draft.dailyTarget.toLong(), 1, 1, 12) { draft = draft.copy(dailyTarget = it.toInt()) }
                }
                HabitType.TIMED -> {
                    Stepper("Sessions per day", draft.sessions.toLong(), 1, 1, 12) { draft = draft.copy(sessions = it.toInt()) }
                    Stepper("Minutes per session", draft.dailyTarget.toLong(), 5, 5, 300) { draft = draft.copy(dailyTarget = it.toInt()) }
                    Text(
                        "Goal ${TimerMath.formatDuration(draft.dailyGoalUnits)} a day. The timer stops itself at the end of each session; pausing keeps the time you've done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                HabitType.STEPS -> Stepper("Steps per day", draft.dailyTarget.toLong(), 500, 500, 50_000) { draft = draft.copy(dailyTarget = it.toInt()) }
            }

            Section("SCHEDULE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val kinds = buildList {
                    add(ScheduleKind.DAILY to "Every day")
                    add(ScheduleKind.WEEKDAYS to "Weekdays")
                    if (!draft.isNegative) add(ScheduleKind.DAYS_PER_WEEK to "Days per week")
                    if (!draft.isNegative && draft.type == HabitType.COUNT) add(ScheduleKind.TIMES_PER_WEEK to "Times per week")
                }
                kinds.forEach { (kind, label) ->
                    FilterChip(
                        selected = draft.schedule.kind == kind,
                        onClick = {
                            draft = draft.copy(
                                schedule = when (kind) {
                                    ScheduleKind.DAILY -> Schedule.Daily
                                    ScheduleKind.WEEKDAYS -> Schedule(kind, weekdays = draft.schedule.weekdays.ifEmpty { DayOfWeek.entries.take(5).toSet() })
                                    ScheduleKind.DAYS_PER_WEEK -> Schedule.daysPerWeek(draft.schedule.perWeek.coerceIn(1, 7).takeIf { draft.schedule.perWeek > 0 } ?: 3)
                                    ScheduleKind.TIMES_PER_WEEK -> Schedule.timesPerWeek(draft.schedule.perWeek.takeIf { it > 0 } ?: 3)
                                },
                                dailyTarget = if (kind == ScheduleKind.TIMES_PER_WEEK) 1 else draft.dailyTarget,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
            when (draft.schedule.kind) {
                ScheduleKind.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).map { weekStart.plus(it.toLong()) }.forEach { d ->
                        val on = d in draft.schedule.weekdays
                        FilterChip(
                            selected = on,
                            onClick = {
                                val set = if (on) draft.schedule.weekdays - d else draft.schedule.weekdays + d
                                draft = draft.copy(schedule = draft.schedule.copy(weekdays = set))
                            },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                        )
                    }
                }
                ScheduleKind.DAYS_PER_WEEK -> {
                    Stepper("Different days per week", draft.schedule.perWeek.toLong(), 1, 1, 7) {
                        draft = draft.copy(schedule = Schedule.daysPerWeek(it.toInt()))
                    }
                    Text("Each day counts once — completing twice on one day doesn't count as two days.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                ScheduleKind.TIMES_PER_WEEK -> Stepper("Times per week", draft.schedule.perWeek.toLong(), 1, 1, 14) {
                    draft = draft.copy(schedule = Schedule.timesPerWeek(it.toInt()))
                }
                ScheduleKind.DAILY -> Unit
            }

            Section("REMINDERS")
            draft.reminders.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d:%02d".format(t.hour, t.minute), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { draft = draft.copy(reminders = draft.reminders - t) }) { Text("REMOVE") }
                }
            }
            if (draft.reminders.size < 6) {
                TextButton(onClick = {
                    if (!Notifications.canPost(context)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    showTime = true
                }) { Text("+ ADD REMINDER") }
            }
            Text(
                "Reminders only fire on scheduled days and are skipped when the habit is already done.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )

            if (habitId != 0L) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { confirmDelete = true }) { Text("DELETE HABIT", color = LocalHighlight.current) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute)
                    if (t !in draft.reminders) draft = draft.copy(reminders = (draft.reminders + t).sorted())
                    showTime = false
                }) { Text("ADD") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("CANCEL") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${draft.name}?") },
            text = { Text("This removes the habit and all of its history and timers. It can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { vm.repository.deleteHabit(habitId); confirmDelete = false; onDeleted() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun withType(h: Habit, type: HabitType): Habit = when (type) {
    HabitType.COUNT -> h.copy(type = type, dailyTarget = 1)
    HabitType.TIMED -> h.copy(type = type, dailyTarget = 30, sessions = 1, isNegative = false,
        schedule = if (h.schedule.kind == ScheduleKind.TIMES_PER_WEEK) Schedule.Daily else h.schedule)
    HabitType.STEPS -> h.copy(type = type, dailyTarget = 10_000, isNegative = false,
        schedule = if (h.schedule.kind == ScheduleKind.TIMES_PER_WEEK) Schedule.Daily else h.schedule)
}

private fun isValid(h: Habit): Boolean = when (h.schedule.kind) {
    ScheduleKind.WEEKDAYS -> h.schedule.weekdays.isNotEmpty()
    ScheduleKind.DAYS_PER_WEEK, ScheduleKind.TIMES_PER_WEEK -> h.schedule.perWeek > 0
    ScheduleKind.DAILY -> true
} && (h.isNegative || h.dailyTarget > 0)

@Composable
private fun Section(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall)
}
