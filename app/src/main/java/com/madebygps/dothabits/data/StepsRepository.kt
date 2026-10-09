package com.madebygps.dothabits.data

import android.content.Context
import android.health.connect.DeviceDataSource
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.os.Build
import android.os.OutcomeReceiver
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.coroutines.resume

enum class HcAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

data class StepsStatus(
    val availability: HcAvailability,
    /** Health Connect counts steps from the phone's own sensor (Android 14+, SDK extension ≥ 20). */
    val onDeviceCounting: Boolean,
    val sdkExtension: Int,
    val readGranted: Boolean,
    val backgroundFeatureAvailable: Boolean,
    val backgroundGranted: Boolean,
) {
    /** Steps can be shown automatically without any third-party tracker. */
    val phoneOnlyWorks: Boolean get() = availability == HcAvailability.AVAILABLE && onDeviceCounting && readGranted
}

/**
 * Reads daily step totals from Health Connect.
 *
 * On Android 14+ with SDK extension 20+, Health Connect itself records steps from the phone's
 * low-power step counter once *any* app holds READ_STEPS — no Fitbit/Google Fit needed.
 * Recording only begins after that grant, so there is no step history from before it.
 * Only the phone's own steps are read: those attributed to "android" (before the June 2026
 * update) or to this device's synthetic package name (after it). Other apps that write steps,
 * such as a companion app mirroring the phone or a wearable, are ignored to avoid double counting.
 * Source: developer.android.com/health-and-fitness/health-connect/features/steps
 */
class StepsRepository(private val context: Context) {

    val readPermission = HealthPermission.getReadPermission(StepsRecord::class)
    val backgroundPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun sdkExtension(): Int = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
        else -> HcAvailability.UNAVAILABLE
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HcAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    suspend fun status(): StepsStatus {
        val ext = sdkExtension()
        val client = client()
        val granted = runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
        val bgFeature = client?.let {
            runCatching {
                it.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                    HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            }.getOrDefault(false)
        } ?: false
        return StepsStatus(
            availability = availability(),
            onDeviceCounting = ext >= 20,
            sdkExtension = ext,
            readGranted = readPermission in granted,
            backgroundFeatureAvailable = bgFeature,
            backgroundGranted = backgroundPermission in granted,
        )
    }

    /** "android" plus this device's synthetic package name, when the platform exposes it. */
    private suspend fun phoneOrigins(): Set<DataOrigin> {
        // The docs list extension 11 for this API; the SDK's own annotations require 22.
        val spn = if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 22) {
            runCatching { currentDeviceSpn() }.getOrNull()
        } else {
            null
        }
        return setOfNotNull(DataOrigin(PHONE_STEPS_ORIGIN), spn?.let(::DataOrigin))
    }

    @androidx.annotation.RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 22)
    private suspend fun currentDeviceSpn(): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val hcm = context.getSystemService(HealthConnectManager::class.java)
        if (hcm == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        hcm.getCurrentDeviceDataSource(
            Runnable::run,
            object : OutcomeReceiver<DeviceDataSource, HealthConnectException> {
                override fun onResult(result: DeviceDataSource) = cont.resume(result.deviceDataOrigin.packageName)
                override fun onError(error: HealthConnectException) = cont.resume(null)
            },
        )
    }

    /**
     * Daily totals for the last [days] days including today. Only the phone's own steps count.
     * Days without phone data are omitted (never invented). Returns null when the read fails.
     */
    suspend fun readDailySteps(days: Int, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long>? {
        val client = client() ?: return null
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).toLong()).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        return try {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1),
                    dataOriginFilter = phoneOrigins(),
                ),
            ).mapNotNull { g ->
                g.result[StepsRecord.COUNT_TOTAL]?.let { g.startTime.toLocalDate() to it }
            }.toMap()
        } catch (e: SecurityException) {
            Log.w(TAG, "step read denied days=$days", e)
            null // permission revoked, or background read not allowed
        } catch (e: IllegalStateException) {
            Log.w(TAG, "step read unavailable days=$days", e)
            null
        } catch (e: android.os.RemoteException) {
            Log.w(TAG, "step read remote failure days=$days", e)
            null
        }
    }

    private companion object {
        const val TAG = "DotHabitsSteps"
    }
}

/** Package name Health Connect uses for the phone's own step counter. */
const val PHONE_STEPS_ORIGIN = "android"
