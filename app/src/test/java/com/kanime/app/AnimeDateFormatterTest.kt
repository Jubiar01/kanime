package com.kanime.app

import com.kanime.app.ui.components.AnimeDateFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AnimeDateFormatterTest {

    private val us = Locale.US

    @Test
    fun parsesMalDateRangeStart() {
        assertEquals(
            "Apr 3, 2023",
            AnimeDateFormatter.formatReleaseDate(
                aired = "Apr 3, 2023 to Jun 26, 2023",
                season = "Spring",
                year = 2023,
                locale = us
            )
        )
    }

    @Test
    fun parsesHyphenRangeAndIsoDates() {
        assertEquals(
            "Oct 12, 2024",
            AnimeDateFormatter.formatReleaseDate(
                aired = "Oct 12, 2024 - Dec 28, 2024",
                locale = us
            )
        )
        assertEquals(
            "Sep 29, 2023",
            AnimeDateFormatter.formatEpisodeDate(
                aired = "2023-09-29",
                locale = us
            )
        )
    }

    @Test
    fun parsesPartialMonthYearAndYearOnly() {
        assertEquals(
            "Apr 2023",
            AnimeDateFormatter.formatReleaseDate(aired = "Apr 2023 to ?", locale = us)
        )
        assertEquals(
            "Oct 2024",
            AnimeDateFormatter.formatReleaseDate(aired = "Oct, 2024", locale = us)
        )
        assertEquals(
            "2025",
            AnimeDateFormatter.formatReleaseDate(aired = "2025 to ?", locale = us)
        )
    }

    @Test
    fun fallsBackToSeasonYearThenYearThenNull() {
        assertEquals(
            "Winter 2024",
            AnimeDateFormatter.formatReleaseDate(aired = "?", season = "WINTER", year = 2024, locale = us)
        )
        assertEquals(
            "2022",
            AnimeDateFormatter.formatReleaseDate(aired = "Not available", season = "TV", year = 2022, locale = us)
        )
        assertNull(
            AnimeDateFormatter.formatReleaseDate(aired = null, season = "", year = 0, locale = us)
        )
        assertNull(
            AnimeDateFormatter.formatReleaseDate(aired = "?", season = "null", year = 0, locale = us)
        )
    }

    @Test
    fun detectsAiringAndUpcomingStatuses() {
        assertTrue(AnimeDateFormatter.isAiringStatus("Currently Airing"))
        assertTrue(AnimeDateFormatter.isAiringStatus("Airing"))
        assertFalse(AnimeDateFormatter.isAiringStatus("Completed"))
        assertTrue(AnimeDateFormatter.isUpcomingStatus("Upcoming"))
        assertTrue(AnimeDateFormatter.isUpcomingStatus("Not yet aired"))
    }
}
