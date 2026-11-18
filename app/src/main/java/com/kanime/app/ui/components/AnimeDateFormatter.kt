package com.kanime.app.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pure, thread-safe, minSdk 24-compatible release date parser & locale formatter
 * for MAL / AniList / Kitsu anime & episode date strings.
 */
object AnimeDateFormatter {

    private val VALID_SEASONS = setOf("spring", "summer", "fall", "autumn", "winter")

    private val FULL_DATE_PATTERNS = listOf(
        "MMM d, yyyy",
        "MMMM d, yyyy",
        "yyyy-MM-dd",
        "d MMM yyyy",
        "MMM d yyyy"
    )

    private val MONTH_YEAR_PATTERNS = listOf(
        "MMM, yyyy",
        "MMM yyyy",
        "MMMM, yyyy",
        "MMMM yyyy",
        "yyyy-MM"
    )

    private val YEAR_ONLY_REGEX = Regex("""^\d{4}$""")
    private val ANY_YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    fun isAiringStatus(status: String?): Boolean =
        status?.contains("air", ignoreCase = true) == true

    fun isUpcomingStatus(status: String?): Boolean =
        status?.contains("upcom", ignoreCase = true) == true ||
            status?.contains("not yet", ignoreCase = true) == true

    /**
     * Extracts and formats the start release date for an anime with fallback order:
     * 1) Parsed start date from [aired] (e.g., "Apr 3, 2023 to Jun 26, 2023" -> "Apr 3, 2023")
     * 2) "$season $year" (when season is a valid calendar season and year > 0)
     * 3) "$year" (when year > 0)
     * 4) null (never "0" or "null")
     */
    fun formatReleaseDate(
        aired: String?,
        season: String? = null,
        year: Int = 0,
        locale: Locale = Locale.getDefault()
    ): String? {
        parseAiredStart(aired, locale)?.let { return it }

        val cleanSeason = season?.trim().orEmpty()
        if (cleanSeason.lowercase(Locale.ROOT) in VALID_SEASONS && year > 0) {
            val titled = cleanSeason.lowercase(Locale.ROOT)
                .replaceFirstChar { it.titlecase(locale) }
            return "$titled $year"
        }

        if (year > 0) {
            return year.toString()
        }

        return null
    }

    /**
     * Parses a single episode aired date string into a locale-formatted short date.
     */
    fun formatEpisodeDate(
        aired: String?,
        locale: Locale = Locale.getDefault()
    ): String? = parseAiredStart(aired, locale)

    internal fun parseAiredStart(
        rawAired: String?,
        locale: Locale = Locale.getDefault()
    ): String? {
        if (rawAired.isNullOrBlank()) return null

        val startToken = extractStartToken(rawAired) ?: return null
        if (startToken == "?" || startToken.equals("unknown", ignoreCase = true) ||
            startToken.equals("not available", ignoreCase = true) ||
            startToken.equals("tba", ignoreCase = true) ||
            startToken.equals("null", ignoreCase = true)
        ) {
            return null
        }

        // 1. Full date (day + month + year)
        for (pattern in FULL_DATE_PATTERNS) {
            val parsed = tryParseStrict(startToken, pattern)
            if (parsed != null) {
                return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(parsed)
            }
        }

        // 2. Partial month + year
        for (pattern in MONTH_YEAR_PATTERNS) {
            val parsed = tryParseStrict(startToken, pattern)
            if (parsed != null) {
                val outFmt = SimpleDateFormat("MMM yyyy", locale)
                return outFmt.format(parsed)
            }
        }

        // 3. Year-only
        if (YEAR_ONLY_REGEX.matches(startToken)) {
            return startToken
        }

        // 4. Last-resort year extraction if token has partial text like "Spring 2024" or "2024?"
        ANY_YEAR_REGEX.find(startToken)?.let { return it.groupValues[1] }

        return null
    }

    private fun extractStartToken(raw: String): String? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        // Split range separators: " to ", " - ", " – ", " — " (preserving ISO yyyy-MM-dd hyphens)
        val firstPart = cleaned
            .split(Regex("""\s+to\s+|\s+[–—-]\s+""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            ?.trimEnd(',', '?', ' ')
            ?: return null
        return firstPart.takeIf { it.isNotEmpty() }
    }

    private fun tryParseStrict(input: String, pattern: String): java.util.Date? {
        return try {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
            }
            val pos = java.text.ParsePosition(0)
            val date = sdf.parse(input, pos)
            if (date != null && pos.index == input.length) date else null
        } catch (_: Exception) {
            null
        }
    }
}
