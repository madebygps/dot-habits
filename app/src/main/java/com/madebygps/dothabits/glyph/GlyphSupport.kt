package com.madebygps.dothabits.glyph

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/** Glyph availability checks. */
object GlyphSupport {
    /** Nothing Phone (3) model prefix, matching the SDK's Glyph.DEVICE_23112 ("A024"). */
    private const val PHONE_3_MODEL = "A024"

    fun isPhone3(): Boolean = Build.MODEL.contains(PHONE_3_MODEL)

    private val toysManager = ComponentName(
        "com.nothing.thirdparty",
        "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
    )

    /** The documented Toys manager intent exists on system builds dated 20250829 or later. */
    fun canOpenToysManager(context: Context): Boolean =
        Intent().setComponent(toysManager).resolveActivity(context.packageManager) != null

    fun openToysManager(context: Context): Boolean = try {
        context.startActivity(Intent().setComponent(toysManager).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
