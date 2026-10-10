package com.madebygps.dothabits.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madebygps.dothabits.R
import com.madebygps.dothabits.data.HABITS_PER_PAGE
import com.madebygps.dothabits.data.MAX_HABITS
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.HabitToday
import com.madebygps.dothabits.domain.HabitTileState
import com.madebygps.dothabits.domain.HabitType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private const val HOLD_MS = 550

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Logged -> {
                    val r = snackbar.showSnackbar("${e.name} logged", actionLabel = "UNDO", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.undo(e.entryId)
                }
                is UiEvent.Message -> snackbar.showSnackbar(e.text, duration = SnackbarDuration.Short)
            }
        }
    }

    val habits = ui.snapshot.habits
    val slots = if (habits.size < MAX_HABITS) habits.size + 1 else habits.size
    val pages = ((slots + HABITS_PER_PAGE - 1) / HABITS_PER_PAGE).coerceAtLeast(1)
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Palette.Black,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner),
        ) {
            Header(ui.snapshot.date, ui.snapshot.doneCount, ui.snapshot.dueCount, onAbout = { showAbout = true })
            if (ui.raw != null && habits.isEmpty()) {
                EmptyState(onAdd = onAdd, onExamples = vm::addExamples, modifier = Modifier.weight(1f))
            } else {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    val pageItems = habits.drop(page * HABITS_PER_PAGE).take(HABITS_PER_PAGE)
                    val showAdd = habits.size < MAX_HABITS && page == pages - 1
                    HabitGrid(
                        items = pageItems,
                        showAdd = showAdd,
                        onHold = { vm.hold(it) },
                        onOpen = onOpen,
                        onToggleTimer = { t -> vm.toggleTimer(t.habit.id, t.timerRunning) },
                        onAdd = onAdd,
                    )
                }
            }
            BottomBar(
                pages = pages,
                current = pager.currentPage,
                onPage = { scope.launch { pager.animateScrollToPage(it) } },
                onStats = onStats,
                onSettings = onSettings,
            )
        }
    }
    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
}

@Composable
private fun Header(date: java.time.LocalDate, done: Int, due: Int, onAbout: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(56.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            if (date != java.time.LocalDate.MIN) {
                DotText(date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) + " " + date.dayOfMonth, dot = 3.dp)
            }
        }
        IconButton(onClick = onAbout, modifier = Modifier.align(Alignment.Center)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "About Dot Habits",
                modifier = Modifier.size(48.dp),
            )
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            if (due > 0) DotText("$done/$due", dot = 3.dp, color = if (done == due) LocalHighlight.current else Palette.Muted)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onExamples: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HabitTile(HabitTileState(), Modifier.size(140.dp)) { DotIcon("dot", Modifier.size(56.dp), LocalHighlight.current) }
        Spacer(Modifier.height(24.dp))
        Text("No habits yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Press and hold a habit tile to log a count or start/pause a timer. Steps update automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) { Text("Add habit") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExamples) { Text("Use example habits") }
    }
}

@Composable
private fun HabitGrid(
    items: List<HabitToday>,
    showAdd: Boolean,
    onHold: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onToggleTimer: (HabitToday) -> Unit,
    onAdd: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val cellW = maxWidth / 2
        val cellH = maxHeight / 3
        val labelSpace = 44.dp
        val ring = minOf(cellW * 0.70f, cellH - labelSpace).coerceAtLeast(72.dp)
        val cells: List<HabitToday?> = items + if (showAdd && items.size < HABITS_PER_PAGE) listOf(null) else emptyList()
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 3) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0 until 2) {
                        val i = row * 2 + col
                        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            when {
                                i < items.size -> HabitCell(items[i], ring, onHold, onOpen, onToggleTimer)
                                i == items.size && cells.size > items.size -> AddCell(ring, onAdd)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitCell(
    t: HabitToday,
    ring: Dp,
    onHold: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onToggleTimer: (HabitToday) -> Unit,
) {
    val hold = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val highlight = LocalHighlight.current
    val tile = HabitTileState.from(t)
    val iconColor = when {
        tile.solid -> Palette.Black
        tile.dimmed -> Palette.Dim
        else -> Palette.Text
    }
    val caption = HabitLabels.caption(t)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(ring)) {
            HabitTile(
                state = tile,
                holdProgress = hold.value,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(percent = 18))
                    .semantics {
                        contentDescription = HabitLabels.accessibility(t)
                        customActions = listOf(
                            CustomAccessibilityAction("Complete") { onHold(t.habit.id); true },
                            CustomAccessibilityAction("Open details") { onOpen(t.habit.id); true },
                        )
                    }
                    .pointerInput(t.habit.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var fired = false
                            val job: Job = scope.launch {
                                hold.snapTo(0f)
                                hold.animateTo(1f, tween(HOLD_MS))
                                fired = true
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onHold(t.habit.id)
                            }
                            val up = waitForUpOrCancellation()
                            val wasTap = up != null && !fired && hold.value < 0.6f
                            job.cancel()
                            scope.launch { hold.animateTo(0f, tween(150)) }
                            if (wasTap) onOpen(t.habit.id)
                        }
                    },
            ) {
                DotIcon(t.habit.icon, Modifier.size(ring * 0.40f), iconColor)
            }
            if (t.habit.type == HabitType.TIMED) {
                PlayPauseButton(
                    running = t.timerRunning,
                    size = ring * 0.30f,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                    onClick = { onToggleTimer(t) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            t.habit.name,
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ring + 32.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ring + 40.dp),
            textAlign = TextAlign.Center,
            color = if (t.needsReview) highlight else Palette.Muted,
        )
    }
}

@Composable
fun PlayPauseButton(running: Boolean, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val highlight = LocalHighlight.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (running) highlight else Palette.Black)
            .border(2.dp, highlight, CircleShape)
            .clickable(role = Role.Button, onClickLabel = if (running) "Pause timer" else "Start timer", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.38f)) {
            val c = if (running) Color.Black else highlight
            if (running) {
                val w = this.size.width * 0.32f
                drawRect(c, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, this.size.height))
                drawRect(c, topLeft = Offset(this.size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, this.size.height))
            } else {
                val p = Path().apply {
                    moveTo(this@Canvas.size.width * 0.12f, 0f)
                    lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
                    lineTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height)
                    close()
                }
                drawPath(p, c)
            }
        }
    }
}

@Composable
private fun AddCell(ring: Dp, onAdd: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(ring)
                .clip(RoundedCornerShape(percent = 18))
                .border(2.dp, Palette.Track, RoundedCornerShape(percent = 18))
                .clickable(onClickLabel = "Add habit", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.displaySmall, color = Palette.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Text("ADD", style = MaterialTheme.typography.labelSmall)
        Text("", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BottomBar(pages: Int, current: Int, onPage: (Int) -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    val highlight = LocalHighlight.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton("stats", "Statistics", onStats)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(pages) { i ->
                Box(
                    Modifier
                        .size(if (i == current) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == current) highlight else Palette.Dim)
                        .clickable(onClickLabel = "Page ${i + 1}") { onPage(i) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        BarButton("settings", "Settings", onSettings)
    }
}

@Composable
private fun BarButton(kind: String, label: String, onClick: () -> Unit) {
    val colors = LocalDotColors.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val dot = size.width / 9f
            if (kind == "stats") {
                // three dot columns of rising height
                listOf(3, 6, 9).forEachIndexed { col, h ->
                    for (r in 0 until h) drawCircle(colors.text, dot * 0.45f, Offset(dot * (1.5f + col * 3f), size.height - dot * (r + 0.5f)))
                }
            } else {
                // dotted gear-like ring
                val r = size.width * 0.38f
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    drawCircle(colors.text, dot * 0.55f, Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat()))
                }
                drawCircle(colors.text, dot * 0.9f, center)
            }
        }
    }
}
