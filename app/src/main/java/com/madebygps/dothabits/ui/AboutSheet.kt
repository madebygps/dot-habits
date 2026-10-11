package com.madebygps.dothabits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.madebygps.dothabits.domain.HabitGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Black,
        contentColor = Palette.Text,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dot Habits", style = MaterialTheme.typography.headlineSmall)
            BuildDetails()
            Text("Made by Gwyneth Peña-Siguenza (@madebygps)")
            Text(
                HabitGuide.summary,
                color = Palette.Muted,
            )
            TextButton(onClick = { uriHandler.openUri("https://github.com/madebygps/dot-habits") }) {
                Text("GitHub Repo")
            }
        }
    }
}
