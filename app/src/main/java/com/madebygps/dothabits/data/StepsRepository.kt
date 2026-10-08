package com.madebygps.dothabits.data

import android.content.Context
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

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
 * Aggregates are read without a DataOrigin filter, so on-device steps (attributed to
 * "android" or the June-2026 synthetic package name) are always included.
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

    /**
     * Daily totals for the last [days] days including today. Days Health Connect has no data
     * for are omitted (never invented). Returns empty when not permitted.
     */
    suspend fun readDailySteps(days: Int, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
        val client = client() ?: return emptyMap()
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).toLong()).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        return try {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).mapNotNull { group ->
                group.result[StepsRecord.COUNT_TOTAL]?.let { group.startTime.toLocalDate() to it }
            }.toMap()
        } catch (_: SecurityException) {
            emptyMap() // permission revoked, or background read not allowed
        } catch (_: IllegalStateException) {
            emptyMap()
        } catch (_: android.os.RemoteException) {
            emptyMap()
        }
    }
}
