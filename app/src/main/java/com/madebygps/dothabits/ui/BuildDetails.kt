package com.madebygps.dothabits.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.madebygps.dothabits.BuildConfig
import com.madebygps.dothabits.R

@Composable
fun BuildDetails() {
    Text(
        stringResource(
            R.string.build_identity,
            BuildConfig.VERSION_NAME,
            BuildConfig.GIT_COMMIT.take(12),
            if (BuildConfig.GIT_DIRTY) stringResource(R.string.build_modified) else "",
            BuildConfig.BUILT_AT,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Muted,
    )
}
