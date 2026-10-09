package com.madebygps.dothabits.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.madebygps.dothabits.data.HcAvailability
import com.madebygps.dothabits.data.HighlightPalette
import com.madebygps.dothabits.dotApp
import com.madebygps.dothabits.glyph.GlyphSupport
import com.madebygps.dothabits.system.Alarms
import com.madebygps.dothabits.system.Notifications
import com.madebygps.dothabits.system.Refresh
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val app = context.dotApp
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings = ui.raw?.settings ?: return
    val steps by vm.steps.collectAsStateWithLifecycle()
    var showToyHelp by remember { mutableStateOf(false) }
    var showHabitPicker by remember { mutableStateOf(false) }
    val habits = ui.raw?.habits.orEmpty().sortedBy { it.position }
    val glyphHabit = ui.snapshot.glyphHabit(settings.glyphHabitId)

    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    var canExact by remember { mutableStateOf(Alarms.canExact(context)) }
    var toysManager by remember { mutableStateOf(GlyphSupport.canOpenToysManager(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifications.canPost(context)
        canExact = Alarms.canExact(context)
        toysManager = GlyphSupport.canOpenToysManager(context)
        vm.refreshSteps()
        onPauseOrDispose { }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = Notifications.canPost(context)
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshSteps()
    }

    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { DotScreenTitle("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header("HIGHLIGHT")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HighlightPalette.forEach { h ->
                    val selected = h.argb == settings.highlight
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) Palette.Text else Color.Transparent, CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color(h.argb))
                            .clickable(onClickLabel = h.name) {
                                scope.launch { app.settings.setHighlight(h.argb); Refresh.afterDataChange(app) }
                            },
                    )
                }
            }

            Header("WEEK STARTS ON")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = settings.weekStart == d,
                        onClick = { scope.launch { app.settings.setWeekStart(d); Refresh.afterDataChange(app) } },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                    )
                }
            }

            Divider()
            Header("STEPS")
            StepsSection(steps, onRequest = {
                val s = steps
                val perms = buildSet {
                    add(app.steps.readPermission)
                    if (s?.backgroundFeatureAvailable == true) add(app.steps.backgroundPermission)
                }
                hcLauncher.launch(perms)
            }, onUpdate = {
                val uri = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) }
            }, onOpenHc = {
                runCatching { context.startActivity(Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")) }
            })

            Divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Header("TOYS")
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { showToyHelp = true },
                    modifier = Modifier.size(40.dp),
                ) { Text("?") }
            }
            PermRow(
                "Toys",
                when {
                    !GlyphSupport.isPhone3() -> "Needs Nothing Phone (3)"
                    toysManager -> "Habit and Timers"
                    else -> "Open Nothing settings to enable toys"
                },
                if (GlyphSupport.isPhone3() && toysManager) "SET UP" else null,
            ) { GlyphSupport.openToysManager(context) }
            if (GlyphSupport.isPhone3()) {
                PermRow(
                    "Displayed habit",
                    glyphHabit?.habit?.name ?: when {
                        habits.isEmpty() -> "No habits yet"
                        settings.glyphHabitId != null -> "Selected habit was deleted"
                        else -> "Not selected"
                    },
                    if (habits.isEmpty()) null else "PICK",
                ) { showHabitPicker = true }
            }

            Divider()
            Header("REMINDERS")
            PermRow(
                "Notifications",
                if (canNotify) "On" else "Off. Reminders and timers can't notify you",
                if (canNotify) null else "ALLOW",
            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            PermRow(
                "Exact alarms",
                if (canExact) "On time" else "May arrive up to 10 minutes late",
                if (canExact) null else "ALLOW",
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                    )
                }
            }

            Divider()
            Header("HABIT ORDER")
            habits.forEachIndexed { i, h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotIcon(h.icon, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(h.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = i > 0, onClick = { scope.launch { app.repository.move(h.id, -1) } }) { Text("↑") }
                    TextButton(enabled = i < habits.lastIndex, onClick = { scope.launch { app.repository.move(h.id, 1) } }) { Text("↓") }
                }
            }

            Divider()
            Header("ABOUT")
            Text(
                "Offline. No account, no tracking. Your data stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showToyHelp) {
        AlertDialog(
            onDismissRequest = { showToyHelp = false },
            title = { Text("Glyph Toys") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("HABIT", style = MaterialTheme.typography.labelSmall)
                    Text("Shows the selected habit's icon and progress.")
                    Text("TIMERS", style = MaterialTheme.typography.labelSmall)
                    Text("Long-press to start or pause. Hold for 2 seconds to switch timers.")
                }
            },
            confirmButton = { TextButton(onClick = { showToyHelp = false }) { Text("OK") } },
        )
    }
    if (showHabitPicker) {
        fun pickHabit(id: Long?) {
            scope.launch {
                app.settings.setGlyphHabit(id)
                showHabitPicker = false
            }
        }
        AlertDialog(
            onDismissRequest = { showHabitPicker = false },
            title = { Text("Displayed habit") },
            text = {
                LazyColumn(Modifier.selectableGroup()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = settings.glyphHabitId == null,
                                    role = Role.RadioButton,
                                    onClick = { pickHabit(null) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.glyphHabitId == null, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text("No habit selected", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    items(habits, key = { it.id }) { habit ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = habit.id == settings.glyphHabitId,
                                    role = Role.RadioButton,
                                    onClick = { pickHabit(habit.id) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = habit.id == settings.glyphHabitId, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            DotIcon(habit.icon, Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHabitPicker = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun StepsSection(
    s: com.madebygps.dothabits.data.StepsStatus?,
    onRequest: () -> Unit,
    onUpdate: () -> Unit,
    onOpenHc: () -> Unit,
) {
    if (s == null) {
        Text("Checking…", style = MaterialTheme.typography.bodySmall, color = Palette.Muted); return
    }
    when {
        s.availability == HcAvailability.UNAVAILABLE ->
            PermRow("Health Connect", "Not available on this phone", null) {}
        s.availability == HcAvailability.UPDATE_REQUIRED ->
            PermRow("Health Connect", "Needs an update", "UPDATE", onUpdate)
        !s.onDeviceCounting ->
            PermRow("Step counting", "This phone can't count steps itself. Another app must add them to Health Connect.", "OPEN", onOpenHc)
        !s.readGranted ->
            PermRow("Step counting", "Off. Counting starts when you allow it", "ALLOW", onRequest)
        else -> {
            PermRow("Step counting", "On", "MANAGE", onOpenHc)
            PermRow(
                "Background refresh",
                when {
                    !s.backgroundFeatureAvailable -> "Not supported. Updates when you open the app"
                    s.backgroundGranted -> "On, about every 15 minutes"
                    else -> "Off. Widgets and Glyph update when you open the app"
                },
                if (s.backgroundFeatureAvailable && !s.backgroundGranted) "ALLOW" else null,
                onRequest,
            )
        }
    }
}

@Composable
private fun PermRow(title: String, status: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Header(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Divider() = HorizontalDivider(color = Palette.Line)
