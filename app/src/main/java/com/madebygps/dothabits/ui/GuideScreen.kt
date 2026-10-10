package com.madebygps.dothabits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.madebygps.dothabits.domain.GuideSection
import com.madebygps.dothabits.domain.HabitGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen() {
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                title = { Text("How Dot Habits works", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HabitGuide.sections.forEach { section ->
                item { GuideSectionContent(section) }
            }
        }
    }
}

@Composable
private fun GuideSectionContent(section: GuideSection) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(section.title.uppercase(), Modifier.semantics { heading() }, style = MaterialTheme.typography.labelMedium, color = LocalHighlight.current)
        section.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun GuideHelpDialog(sections: List<GuideSection>, onDismiss: () -> Unit, onGuide: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading this screen") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                sections.forEach { GuideSectionContent(it) }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onGuide() }) { Text("FULL GUIDE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}
