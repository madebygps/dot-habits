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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.dotApp
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings = ui.raw?.settings ?: return
    val steps by vm.steps.collectAsStateWithLifecycle()

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

    fun refreshAll() = scope.launch { Refresh.afterDataChange(app) }

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
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("BACK") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header("HIGHLIGHT")
            Text("Used by the app and widgets. The Glyph Matrix is always monochrome.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
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
            Text("Changes how weekly goals and weekly streaks are grouped. History is recalculated.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)

            Divider()
            Header("STEPS · HEALTH CONNECT")
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
            Header("GLYPH MATRIX")
            Text(
                when {
                    !GlyphSupport.sdkBundled -> "This build was made without the Nothing Glyph Matrix SDK, so the Glyph Toy is disabled. See README to add it."
                    !GlyphSupport.isPhone3() -> "Glyph Toy is built in, but this device doesn't report as Nothing Phone (3)."
                    else -> "Add “Dot Habits” in Glyph Toys, then short-press the Glyph Button to reach it. Long-press switches between today's progress and the active timer. It never completes habits or controls timers."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (GlyphSupport.sdkBundled) {
                var brightness by remember(settings.glyphBrightness) { mutableFloatStateOf(settings.glyphBrightness.toFloat()) }
                Text("Brightness ${(brightness / 255f * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = { scope.launch { app.settings.setGlyphBrightness(brightness.toInt()) } },
                    valueRange = 16f..255f,
                )
                OutlinedButton(enabled = toysManager, onClick = { GlyphSupport.openToysManager(context) }) {
                    Text("Open Glyph Toys manager")
                }
                if (!toysManager) {
                    Text(
                        "Glyph Toys manager not found on this system build. Open Settings › Glyph Interface › Glyph Toys instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
            }

            Divider()
            Header("REMINDERS")
            PermRow(
                "Notifications",
                if (canNotify) "Allowed" else "Not allowed — reminders and the timer notification are hidden",
                if (canNotify) null else "ALLOW",
            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            PermRow(
                "Exact alarms",
                if (canExact) "Reminders fire on the minute" else "Reminders may arrive up to 10 minutes late",
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
            val habits = ui.raw?.habits.orEmpty().sortedBy { it.position }
            Text("${habits.size} / ${com.madebygps.dothabits.data.MAX_HABITS} habits · ${com.madebygps.dothabits.data.HABITS_PER_PAGE} per page", style = MaterialTheme.typography.labelSmall)
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
                "Dot Habits is offline and local-only: no account, no server, no analytics. Data stays in this phone's app storage.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(32.dp))
        }
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
        Text("Checking…", style = MaterialTheme.typography.bodySmall); return
    }
    val line = when {
        s.availability == HcAvailability.UNAVAILABLE -> "Health Connect isn't available on this device, so step habits can't update."
        s.availability == HcAvailability.UPDATE_REQUIRED -> "Health Connect needs an update before steps can be read."
        !s.onDeviceCounting -> "This system's Health Connect (extension ${s.sdkExtension}) doesn't count phone steps by itself — it needs extension 20+. " +
            "Steps will only appear if another app or device writes them to Health Connect."
        !s.readGranted -> "Health Connect can count steps from the phone itself. Allow Dot Habits to read steps to start counting — there's no step history from before you allow it."
        else -> "Counting steps on this phone via Health Connect (extension ${s.sdkExtension})."
    }
    Text(line, style = MaterialTheme.typography.bodySmall)
    if (s.availability == HcAvailability.AVAILABLE && s.readGranted) {
        Text(
            if (!s.backgroundFeatureAvailable) "Background reading isn't supported here; widget and Glyph steps refresh when you open the app."
            else if (s.backgroundGranted) "Background refresh: on (about hourly)."
            else "Background refresh: off — widget and Glyph steps update when you open the app.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (s.availability) {
            HcAvailability.UPDATE_REQUIRED -> OutlinedButton(onClick = onUpdate) { Text("Update Health Connect") }
            HcAvailability.AVAILABLE -> {
                if (!s.readGranted || (s.backgroundFeatureAvailable && !s.backgroundGranted)) {
                    OutlinedButton(onClick = onRequest) { Text(if (s.readGranted) "Allow background" else "Allow steps") }
                }
                TextButton(onClick = onOpenHc) { Text("HEALTH CONNECT") }
            }
            HcAvailability.UNAVAILABLE -> Unit
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
