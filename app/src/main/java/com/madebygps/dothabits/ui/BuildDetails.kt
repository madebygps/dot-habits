package com.madebygps.dothabits.ui

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.madebygps.dothabits.BuildConfig
import com.madebygps.dothabits.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun BuildDetails() {
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(LocalContext.current)) "yMMMdHmz" else "yMMMdhmz"
    val builtAt = formatBuildTime(
        BuildConfig.BUILT_AT,
        DateFormat.getBestDateTimePattern(locale, skeleton),
        locale,
        TimeZone.getDefault(),
    )
    Text(
        stringResource(
            R.string.build_identity,
            BuildConfig.VERSION_NAME,
            BuildConfig.GIT_COMMIT.take(12),
            if (BuildConfig.GIT_DIRTY) stringResource(R.string.build_modified) else "",
            builtAt,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Muted,
    )
}

internal fun formatBuildTime(
    builtAt: String,
    pattern: String,
    locale: Locale,
    timeZone: TimeZone,
): String = SimpleDateFormat(pattern, locale).apply {
    this.timeZone = timeZone
}.format(Date.from(Instant.parse(builtAt)))
