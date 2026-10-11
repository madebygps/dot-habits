package com.madebygps.dothabits.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.madebygps.dothabits.R

object LiveProgressBattery {
    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun openSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.e("DotHabitsTimer", "Battery optimization settings unavailable", e)
            Toast.makeText(context, R.string.battery_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
