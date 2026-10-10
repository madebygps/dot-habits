package com.madebygps.dothabits.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId

/** Runs the actual migration's conversion against deterministic cursor fixtures. */
class TimerMigrationTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 10, 10)
    private val start = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private data class Interval(val start: Long, val end: Long?, val state: String = "CLOSED", val lastAlive: Long = start, val limit: Long? = null)
    private data class Statement(val sql: String, val args: List<Any?>)

    private fun migrate(
        entries: List<List<Long>> = emptyList(), intervals: List<Interval> = emptyList(),
        now: Long = start + 86_400_000L / 4, configuredMinutes: Long = 25L,
    ): List<Statement> {
        val statements = mutableListOf<Statement>()
        val db = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            when (method.name) {
                "query" -> {
                    val sql = args!![0] as String
                    val rows = when {
                        "FROM habits" in sql -> listOf(listOf(1L, configuredMinutes))
                        "FROM entries" in sql -> entries
                        "FROM timer_sessions" in sql -> {
                            intervals.map { listOf<Any?>(it.start, it.end, it.state, it.lastAlive, it.limit) }
                        }
                        else -> error("Unexpected query: $sql")
                    }
                    var position = -1
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Cursor::class.java)) { _, cursorMethod, cursorArgs ->
                        when (cursorMethod.name) {
                            "moveToNext" -> ++position < rows.size
                            "getLong" -> rows[position][cursorArgs!![0] as Int] as Long
                            "getString" -> rows[position][cursorArgs!![0] as Int] as String
                            "isNull" -> rows[position][cursorArgs!![0] as Int] == null
                            "close" -> null
                            else -> error("Unexpected cursor call: ${cursorMethod.name}")
                        }
                    } as Cursor
                }
                "execSQL" -> {
                    statements += Statement(args!![0] as String, (args.getOrNull(1) as? Array<*>)?.toList().orEmpty())
                    null
                }
                else -> error("Unexpected database call: ${method.name}")
            }
        } as SupportSQLiteDatabase
        DotDatabase.migrate(db, now)
        return statements
    }

    private fun credits(statements: List<Statement>) = statements.filter { it.sql.startsWith("INSERT INTO entries") }

    @Test fun legacyPausedIntervalsAggregateIntoOnlyWholeCompletedSessions() {
        val statements = migrate(intervals = listOf(
            Interval(start, start + 600_123), Interval(start + 700_000, start + 1_600_877),
            Interval(start + 2_000_000, start + 2_300_000),
        ))
        val credit = credits(statements).single().args
        assertEquals(listOf(1L, day.toEpochDay(), 1500L, start + 2_300_000), credit)
        assertTrue(statements.any { it.sql == "DROP TABLE timer_sessions" })
        val ddl = statements.single { it.sql.startsWith("CREATE TABLE") }.sql
        assertTrue(ddl.contains("remainingMs INTEGER NOT NULL"))
        assertFalse(ddl.contains("lastAlive"))
    }

    @Test fun signedLegacyCorrectionsApplyBeforeWholeSessionRounding() {
        val statements = migrate(entries = listOf(listOf(day.toEpochDay(), -1500L, start + 4_000_000)),
            intervals = listOf(Interval(start, start + 3_300_000)))
        assertEquals(1500L, credits(statements).single().args[2])
        assertEquals(start + 4_000_000, credits(statements).single().args[3])
        assertTrue(credits(migrate(entries = listOf(listOf(day.toEpochDay(), -5000L, start)),
            intervals = listOf(Interval(start, start + 3_300_000)))).isEmpty())
    }

    @Test fun legacyPartialIntervalsNeverCreateGuessedCredit() {
        assertTrue(credits(migrate(intervals = listOf(
            Interval(start, null, "RUNNING", limit = 1500), Interval(start - 9_000_000, start - 8_000_000, "NEEDS_REVIEW", lastAlive = start - 8_000_000, limit = 1500),
            Interval(start - 86_400_000, start - 86_400_000 + 1_499_999),
        ), now = start + 100_000)).isEmpty())
    }

    @Test fun completedCreditSurvivesLaterDurationEdits() {
        val rows = credits(migrate(configuredMinutes = 60, intervals = listOf(
            Interval(start, start + 1_500_000, limit = 1500),
            Interval(start + 2_000_000, start + 2_900_000, limit = 1500),
        )))
        assertEquals(1500L, rows.single().args[2])
    }

    @Test fun manualCompletionDuringRunningSessionDecodesAgainstRecordedBase() {
        // 10 minutes into a 25 minute run, a manual completed-session edit stored +900s.
        for (now in listOf(start + 600_000, start + 21_600_000, start + 86_400_000)) {
            val rows = credits(migrate(entries = listOf(listOf(day.toEpochDay(), 900L, start + 600_000)),
                intervals = listOf(Interval(start, null, "RUNNING", lastAlive = start + 300_000, limit = 1500)),
                now = now))
            assertEquals(1500L, rows.single().args[2])
            assertEquals(start + 600_000, rows.single().args[3])
        }
    }

    @Test fun staleRunningBeforeRebootNeverCompletesAtMigrationTime() {
        assertTrue(credits(migrate(intervals = listOf(
            Interval(start, null, "RUNNING", lastAlive = start + 300_000, limit = 1500),
        ), now = start + 21_600_000)).isEmpty())
    }

    @Test fun fullLastAliveCheckpointStillDiscardsUnfinishedRunningAndReviewedRows() {
        for (state in listOf("RUNNING", "NEEDS_REVIEW")) {
            assertTrue(credits(migrate(intervals = listOf(
                Interval(start, null, state, lastAlive = start + 9_000_000, limit = 1500),
            ))).isEmpty())
        }
    }

    @Test fun discardedRunningCannotTopUpLegacyClosedPartialCredit() {
        val rows = credits(migrate(intervals = listOf(
            Interval(start, start + 1_800_000, limit = 1500),
            Interval(start + 2_000_000, null, "RUNNING", lastAlive = start + 2_900_000, limit = 1500),
        )))
        assertEquals(1500L, rows.single().args[2])
    }

    @Test fun explicitManualCompletionPreservesEarlierClosedCreditWithoutAdvancingRunning() {
        val rows = credits(migrate(entries = listOf(listOf(day.toEpochDay(), 900L, start + 2_600_000)),
            intervals = listOf(
                Interval(start, start + 1_500_000, limit = 1500),
                Interval(start + 2_000_000, null, "RUNNING", lastAlive = start + 2_300_000, limit = 1500),
            ), now = start + 21_600_000))
        assertEquals(3000L, rows.single().args[2])
    }

    @Test fun correctionOnAnotherDayCannotCreditStaleRunning() {
        val rows = credits(migrate(entries = listOf(listOf(day.plusDays(1).toEpochDay(), 1500L, start + 86_400_000)),
            intervals = listOf(Interval(start, null, "RUNNING", lastAlive = start + 1_500_000, limit = 1500)),
            now = start + 86_400_000))
        assertEquals(listOf(1L, day.plusDays(1).toEpochDay(), 1500L, start + 86_400_000),
            rows.single().args)
    }

    @Test fun explicitManualCorrectionNeverExceedsStoredRunningLimit() {
        val rows = credits(migrate(entries = listOf(listOf(day.toEpochDay(), 1500L, start + 9_000_000)),
            intervals = listOf(Interval(start, null, "RUNNING", lastAlive = start + 300_000, limit = 1500))))
        assertEquals(3000L, rows.single().args[2])
    }

    @Test fun reviewedRunCountsToLastAliveAndManualOnlyDaysStayWhole() {
        val rows = credits(migrate(
            entries = listOf(listOf(day.toEpochDay(), 900L, start + 600_000), listOf(day.minusDays(1).toEpochDay(), 3000L, start)),
            intervals = listOf(Interval(start, start + 9_000_000, "NEEDS_REVIEW", lastAlive = start + 600_000, limit = 1500)),
        )).associate { it.args[1] as Long to it.args[2] as Long }
        assertEquals(mapOf(day.toEpochDay() to 1500L, day.minusDays(1).toEpochDay() to 3000L), rows)
    }

    @Test fun reviewCorrectionCannotInferTimeBeyondLastAliveOrCorrectionCreation() {
        for ((lastAlive, correction) in listOf(
            (start + 300_000) to (start + 600_000),
            (start + 600_000) to (start + 300_000),
        )) {
            assertTrue(credits(migrate(entries = listOf(listOf(day.toEpochDay(), 900L, correction)),
                intervals = listOf(Interval(start, null, "NEEDS_REVIEW", lastAlive = lastAlive, limit = 1500)))).isEmpty())
        }
    }

    @Test fun completedLegacyCrossMidnightCreditStaysOnItsRecordedDays() {
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val statements = migrate(intervals = listOf(Interval(midnight - 1_500_000, midnight + 1_800_000)))
        val rows = credits(statements).associate { it.args[1] as Long to it.args[2] as Long }
        assertEquals(mapOf(day.toEpochDay() to 1500L, day.plusDays(1).toEpochDay() to 1500L), rows)
    }
}
