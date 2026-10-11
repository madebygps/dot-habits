package com.madebygps.dothabits.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.madebygps.dothabits.data.HighlightPalette
import com.madebygps.dothabits.domain.HabitGuide

/**
 * Health Connect permission rationale / privacy policy screen. Health Connect links here
 * (ACTION_SHOW_PERMISSIONS_RATIONALE and the VIEW_PERMISSION_USAGE alias).
 */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DotTheme(Color(HighlightPalette.first().argb)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Palette.Black)
                        .safeDrawingPadding()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Dot Habits · Privacy", style = MaterialTheme.typography.titleLarge)
                    HabitGuide.privacy.paragraphs.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { finish() }) { Text("CLOSE") }
                }
            }
        }
    }
}
