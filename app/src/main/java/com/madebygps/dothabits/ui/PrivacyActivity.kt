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
                    Text(PRIVACY_TEXT, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { finish() }) { Text("CLOSE") }
                }
            }
        }
    }

    private companion object {
        const val PRIVACY_TEXT =
            "Dot Habits reads only your daily step totals from Health Connect, to fill in step habits " +
                "(for example “Walk 10,000 steps”).\n\n" +
                "• Steps are read, never written, and only as daily totals.\n" +
                "• Optional background access lets widgets and the Glyph Toy update about hourly while the app is closed.\n" +
                "• Daily totals are cached on this phone so history and streaks work offline.\n" +
                "• Nothing leaves the device: no account, no server, no analytics, no ads.\n\n" +
                "You can revoke access at any time in Health Connect. Previously cached daily totals stay " +
                "until you delete the step habit or uninstall the app."
    }
}
