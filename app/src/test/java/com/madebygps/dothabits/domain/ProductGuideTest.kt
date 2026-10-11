package com.madebygps.dothabits.domain

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductGuideTest {
    @Test fun publishedGuideMatchesAppContent() {
        val guide = File(checkNotNull(System.getProperty("productGuide.path")))
        val markdown = buildString {
            append("# How Dot Habits works\n\n")
            append("<!-- Generated from HabitGuide.kt. Do not edit directly. -->\n\n")
            HabitGuide.sections.forEach { section ->
                append("## ${section.title}\n\n")
                section.paragraphs.forEach { append("$it\n\n") }
            }
        }
        if (java.lang.Boolean.getBoolean("productGuide.update")) {
            checkNotNull(guide.parentFile).mkdirs()
            guide.writeText(markdown, Charsets.UTF_8)
        }
        assertTrue("Missing product guide. $REGENERATE", guide.isFile)
        assertEquals("Product guide is stale. $REGENERATE", markdown, guide.readText(Charsets.UTF_8))
    }

    @Test fun sharedHelpIsIncludedInFullGuide() {
        assertTrue(HabitGuide.sections.contains(HabitGuide.statisticsHelp))
        assertTrue(HabitGuide.sections.contains(HabitGuide.privacy))
        val publishedParagraphs = HabitGuide.sections.flatMap { it.paragraphs }
        assertTrue(publishedParagraphs.containsAll(HabitGuide.toyHelp.paragraphs))
        HabitType.entries.forEach { type ->
            listOf(Schedule.Daily, Schedule.daysPerWeek(3)).forEach { schedule ->
                val habit = Habit(name = "Example", icon = "dot", type = type, dailyTarget = 1,
                    schedule = schedule, createdOn = LocalDate.of(2026, 1, 1))
                listOf(habit, habit.copy(isNegative = true)).forEach {
                    assertTrue(publishedParagraphs.containsAll(HabitGuide.contextual(it).flatMap { it.paragraphs }))
                }
            }
        }
    }

    private companion object {
        const val REGENERATE = "Run ./gradlew :app:testDebugUnitTest --tests '*ProductGuideTest' -PupdateProductGuide"
    }
}
