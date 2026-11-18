package com.kanime.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * TMDB REST API client (primary art & episode still source), ported from
 * SH0MIK/Anivault-API (src/scrapers/tmdb.ts).
 *
 * Used for:
 * - Poster / cover / backdrop / transparent logo art (TV season-specific + Movie fallback)
 * - Episode stills, titles, air dates (with absolute -> season/episode mapping + 21-day air-date verification)
 * - Total episode count cross-check for currently-airing shows
 *
 * Gracefully returns null when TMDB_API_KEY is not configured so Kitsu -> AniList fallbacks run cleanly.
 */
object TmdbApi {

    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val ANIMATION_GENRE_ID = 16
    private const val EXPECTED_AIRED_TOLERANCE_MS = 21L * 24L * 60L * 60L * 1000L

    private val ROMAN_TO_NUM = mapOf(
        "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5,
        "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10
    )

    data class SeasonHint(
        val base: String,
        val season: Int?
    )

    data class TmdbTitleCandidates(
        val titles: List<String>,
        val seasonHint: Int?
    )

    data class TmdbAnimeImages(
        val showId: Int,
        val showName: String,
        val season: Int?,
        val poster: String?,
        val posterOriginal: String?,
        val backdrop: String?,
        val backdropOriginal: String?,
        val logo: String?,
        val logoOriginal: String?
    )

    data class TmdbSeasonInfo(
        val seasonNumber: Int,
        val episodeCount: Int
    )

    data class TmdbEpisodeCandidate(
        val season: Int,
        val epInSeason: Int
    )

    data class TmdbEpisodeData(
        val showId: Int,
        val showName: String,
        val season: Int,
        val title: String?,
        val aired: String?,
        val stillPath: String?,
        val thumbnail: String?,
        val thumbnailOriginal: String?
    )

    private data class TmdbMatch(
        val id: Int,
        val name: String
    )

    fun isConfigured(): Boolean = CatalogConfig.tmdbApiKey.isNotBlank()

    // ── Season-hint extraction ───────────────────────────────────────────────

    fun extractSeasonHint(title: String): SeasonHint {
        val wordPatterns = listOf(
            Regex("\\s+season\\s+(\\d{1,2})\\s*$", RegexOption.IGNORE_CASE),
            Regex("\\s+(\\d{1,2})(?:st|nd|rd|th)\\s+season\\s*$", RegexOption.IGNORE_CASE),
            Regex("\\s+part\\s+(\\d{1,2})\\s*$", RegexOption.IGNORE_CASE),
            Regex("\\s+cour\\s+(\\d{1,2})\\s*$", RegexOption.IGNORE_CASE)
        )
        for (re in wordPatterns) {
            val m = re.find(title)
            if (m != null) {
                val base = title.substring(0, m.range.first).trim()
                val num = m.groupValues[1].toIntOrNull()
                if (base.isNotBlank() && num != null) {
                    return SeasonHint(base, num)
                }
            }
        }

        val romanMatch = Regex("\\s+(I{2,3}|IV|VI{0,3}|IX|X)\\s*$", RegexOption.IGNORE_CASE).find(title)
        if (romanMatch != null) {
            val season = ROMAN_TO_NUM[romanMatch.groupValues[1].lowercase()]
            if (season != null) {
                val base = title.substring(0, romanMatch.range.first).trim().replace(Regex("[:\\-–]\\s*$"), "").trim()
                if (base.isNotBlank()) return SeasonHint(base, season)
            }
        }

        val digitMatch = Regex("\\s+(\\d{1,2})\\s*$").find(title)
        if (digitMatch != null) {
            val n = digitMatch.groupValues[1].toIntOrNull()
            if (n != null && n in 2..10) {
                val base = title.substring(0, digitMatch.range.first).trim().replace(Regex("[:\\-–]\\s*$"), "").trim()
                if (base.isNotBlank()) return SeasonHint(base, n)
            }
        }

        return SeasonHint(title, null)
    }

    fun computeTmdbTitleCandidates(rawTitles: List<String>): TmdbTitleCandidates {
        var seasonHint: Int? = null
        val baseTitles = mutableListOf<String>()
        for (t in rawTitles) {
            val hint = extractSeasonHint(t)
            if (hint.season != null && seasonHint == null) {
                seasonHint = hint.season
            }
            baseTitles.add(hint.base)
        }
        val combined = (baseTitles + rawTitles).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return TmdbTitleCandidates(titles = combined, seasonHint = seasonHint)
    }

    // ── HTTP Helper ──────────────────────────────────────────────────────────

    private suspend fun getJson(path: String, params: Map<String, String> = emptyMap()): JSONObject? =
        withContext(Dispatchers.IO) {
            val apiKey = CatalogConfig.tmdbApiKey
            if (apiKey.isBlank()) return@withContext null
            try {
                val urlBuilder = "$BASE_URL$path".toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", apiKey)
                params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Accept", "application/json")
                    .header("User-Agent", CatalogConfig.DESKTOP_USER_AGENT)
                    .build()

                CatalogConfig.httpClient.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) return@withContext null
                    val body = res.body.string()
                    JSONObject(body)
                }
            } catch (_: Exception) {
                null
            }
        }

    // ── Image Selection ──────────────────────────────────────────────────────

    private fun pickBestImage(arr: JSONArray?, textlessFirst: Boolean): JSONObject? {
        if (arr == null || arr.length() == 0) return null
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .sortedByDescending { it.optDouble("vote_average", 0.0) }
        if (list.isEmpty()) return null

        fun iso(obj: JSONObject): String? =
            if (obj.isNull("iso_639_1")) null else obj.optString("iso_639_1").takeIf { it.isNotBlank() && it != "null" }

        return if (textlessFirst) {
            list.firstOrNull { iso(it) == null }
                ?: list.firstOrNull { iso(it) == "en" }
                ?: list.first()
        } else {
            list.firstOrNull { iso(it) == "en" }
                ?: list.firstOrNull { iso(it) == null }
                ?: list.first()
        }
    }

    private fun isAnimated(obj: JSONObject): Boolean {
        val genres = obj.optJSONArray("genre_ids") ?: return false
        for (i in 0 until genres.length()) {
            if (genres.optInt(i) == ANIMATION_GENRE_ID) return true
        }
        return false
    }

    private fun isJapanese(obj: JSONObject): Boolean {
        if (obj.optString("original_language") == "ja") return true
        val countries = obj.optJSONArray("origin_country") ?: return false
        for (i in 0 until countries.length()) {
            if (countries.optString(i) == "JP") return true
        }
        return false
    }

    // ── Search TV & Movie (Animation-only guard) ─────────────────────────────

    private suspend fun searchShow(animeTitle: String): TmdbMatch? {
        val cacheKey = "tmdb:search:tv:${animeTitle.lowercase()}"
        when (val cached = CatalogCache.lookup<TmdbMatch>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val data = getJson("/search/tv", mapOf("query" to animeTitle, "language" to "en-US")) ?: return null
        val resultsArr = data.optJSONArray("results") ?: return null
        val results = (0 until resultsArr.length()).mapNotNull { resultsArr.optJSONObject(it) }
        val show = results.firstOrNull { isAnimated(it) && isJapanese(it) }
            ?: results.firstOrNull { isAnimated(it) }

        val match = show?.let { TmdbMatch(it.optInt("id"), it.optString("name")) }?.takeIf { it.id > 0 }
        CatalogCache.set(cacheKey, match, CatalogCache.Bucket.MAPPING)
        return match
    }

    private suspend fun searchMovie(animeTitle: String): TmdbMatch? {
        val cacheKey = "tmdb:search:movie:${animeTitle.lowercase()}"
        when (val cached = CatalogCache.lookup<TmdbMatch>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val data = getJson("/search/movie", mapOf("query" to animeTitle, "language" to "en-US")) ?: return null
        val resultsArr = data.optJSONArray("results") ?: return null
        val results = (0 until resultsArr.length()).mapNotNull { resultsArr.optJSONObject(it) }
        val movie = results.firstOrNull { isAnimated(it) && isJapanese(it) }
            ?: results.firstOrNull { isAnimated(it) }

        val match = movie?.let { TmdbMatch(it.optInt("id"), it.optString("title")) }?.takeIf { it.id > 0 }
        CatalogCache.set(cacheKey, match, CatalogCache.Bucket.MAPPING)
        return match
    }

    // ── Anime Art (Poster / Backdrop / Transparent Logo) ─────────────────────

    suspend fun getAnimeImages(animeTitle: String, seasonHint: Int? = null): TmdbAnimeImages? {
        if (!isConfigured()) return null
        val cacheKey = "tmdb:images:v2:${animeTitle.lowercase()}:s${seasonHint ?: ""}"
        when (val cached = CatalogCache.lookup<TmdbAnimeImages>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val show = searchShow(animeTitle)
        if (show == null) {
            val movie = searchMovie(animeTitle)
            if (movie == null) {
                CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
                return null
            }
            val imgRes = getJson("/movie/${movie.id}/images", mapOf("include_image_language" to "en,ja,null"))
            val poster = pickBestImage(imgRes?.optJSONArray("posters"), textlessFirst = false)
            val backdrop = pickBestImage(imgRes?.optJSONArray("backdrops"), textlessFirst = true)
            val logo = pickBestImage(imgRes?.optJSONArray("logos"), textlessFirst = false)

            if (poster == null && backdrop == null && logo == null) {
                CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
                return null
            }

            val posterPath = poster?.optString("file_path")?.takeIf { it.isNotBlank() }
            val backdropPath = backdrop?.optString("file_path")?.takeIf { it.isNotBlank() }
            val logoPath = logo?.optString("file_path")?.takeIf { it.isNotBlank() }

            val result = TmdbAnimeImages(
                showId = movie.id,
                showName = movie.name,
                season = null,
                poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                posterOriginal = posterPath?.let { "https://image.tmdb.org/t/p/original$it" },
                backdrop = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                backdropOriginal = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
                logo = logoPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                logoOriginal = logoPath?.let { "https://image.tmdb.org/t/p/original$it" }
            )
            CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
            return result
        }

        val imgRes = getJson("/tv/${show.id}/images", mapOf("include_image_language" to "en,ja,null"))
        val backdrop = pickBestImage(imgRes?.optJSONArray("backdrops"), textlessFirst = true)
        val logo = pickBestImage(imgRes?.optJSONArray("logos"), textlessFirst = false)

        var poster: JSONObject? = null
        var posterSeason: Int? = null
        val seasonsToTry = listOfNotNull(seasonHint, 1).filter { it > 0 }.distinct()
        for (s in seasonsToTry) {
            val seasonImgRes = getJson("/tv/${show.id}/season/$s/images", mapOf("include_image_language" to "en,ja,null"))
            val best = pickBestImage(seasonImgRes?.optJSONArray("posters"), textlessFirst = false)
            if (best != null) {
                poster = best
                posterSeason = s
                break
            }
        }
        if (poster == null) {
            poster = pickBestImage(imgRes?.optJSONArray("posters"), textlessFirst = false)
        }

        if (poster == null && backdrop == null && logo == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
            return null
        }

        val posterPath = poster?.optString("file_path")?.takeIf { it.isNotBlank() }
        val backdropPath = backdrop?.optString("file_path")?.takeIf { it.isNotBlank() }
        val logoPath = logo?.optString("file_path")?.takeIf { it.isNotBlank() }

        val result = TmdbAnimeImages(
            showId = show.id,
            showName = show.name,
            season = posterSeason,
            poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            posterOriginal = posterPath?.let { "https://image.tmdb.org/t/p/original$it" },
            backdrop = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            backdropOriginal = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
            logo = logoPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            logoOriginal = logoPath?.let { "https://image.tmdb.org/t/p/original$it" }
        )
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }

    // ── Seasons & Absolute Episode Mapping ───────────────────────────────────

    suspend fun getShowSeasons(showId: Int): List<TmdbSeasonInfo> {
        val cacheKey = "tmdb:seasons:$showId"
        CatalogCache.get<List<TmdbSeasonInfo>>(cacheKey)?.let { return it }

        val res = getJson("/tv/$showId") ?: return emptyList()
        val arr = res.optJSONArray("seasons") ?: return emptyList()
        val seasons = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .filter { it.optInt("season_number", 0) > 0 }
            .map { TmdbSeasonInfo(it.optInt("season_number"), it.optInt("episode_count", 0)) }
            .sortedBy { it.seasonNumber }

        CatalogCache.set(cacheKey, seasons, CatalogCache.Bucket.MAPPING)
        return seasons
    }

    fun mapAbsoluteEpisode(seasons: List<TmdbSeasonInfo>, absoluteEp: Int): List<TmdbEpisodeCandidate> {
        var cumulative = 0
        for (s in seasons) {
            if (s.episodeCount <= 0) continue
            val before = cumulative
            cumulative += s.episodeCount
            if (absoluteEp <= cumulative) {
                val relative = absoluteEp - before
                val candidates = mutableListOf(TmdbEpisodeCandidate(s.seasonNumber, relative))
                if (absoluteEp != relative) {
                    candidates.add(TmdbEpisodeCandidate(s.seasonNumber, absoluteEp))
                }
                return candidates
            }
        }
        return emptyList()
    }

    private suspend fun buildEpisodeCandidates(
        showId: Int,
        epNum: Int,
        seasonHint: Int?
    ): List<TmdbEpisodeCandidate> {
        val candidates = mutableListOf<TmdbEpisodeCandidate>()
        val seasons = getShowSeasons(showId)
        candidates.addAll(mapAbsoluteEpisode(seasons, epNum))

        for (season in listOfNotNull(seasonHint, 1, 2)) {
            if (season <= 0) continue
            if (candidates.none { it.season == season && it.epInSeason == epNum }) {
                candidates.add(TmdbEpisodeCandidate(season, epNum))
            }
        }
        return candidates
    }

    // ── Air-Date Sanity Check (21-day tolerance) ─────────────────────────────

    private fun parseDateMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val cleaned = dateStr.trim()
        val patterns = listOf("yyyy-MM-dd", "MMM d, yyyy", "MMM dd, yyyy", "yyyy-MM-dd'T'HH:mm:ss")
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = fmt.parse(cleaned)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun airedMismatch(candidateAired: String?, expectedAired: String?): Boolean {
        val a = parseDateMillis(candidateAired) ?: return false
        val b = parseDateMillis(expectedAired) ?: return false
        return abs(a - b) > EXPECTED_AIRED_TOLERANCE_MS
    }

    // ── Episode Data (Title + Air Date + Still) ──────────────────────────────

    suspend fun getEpisodeData(
        animeTitle: String,
        epNum: Int,
        seasonHint: Int? = null,
        expectedAired: String? = null
    ): TmdbEpisodeData? {
        if (!isConfigured()) return null
        val cacheKey = "tmdb:epdata:v2:${animeTitle.lowercase()}:s${seasonHint ?: ""}:$epNum"
        when (val cached = CatalogCache.lookup<TmdbEpisodeData>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> {
                val value = cached.value
                if (value == null || !airedMismatch(value.aired, expectedAired)) {
                    return value
                }
            }
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val show = searchShow(animeTitle)
        if (show == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.EPISODES)
            return null
        }

        val candidates = buildEpisodeCandidates(show.id, epNum, seasonHint)
        for (c in candidates) {
            val epRes = getJson("/tv/${show.id}/season/${c.season}/episode/${c.epInSeason}") ?: continue
            val name = epRes.optString("name").takeIf { it.isNotBlank() && it != "null" }
            val airDate = epRes.optString("air_date").takeIf { it.isNotBlank() && it != "null" }
            val still = epRes.optString("still_path").takeIf { it.isNotBlank() && it != "null" }

            if (name != null || still != null) {
                if (airedMismatch(airDate, expectedAired)) continue
                val result = TmdbEpisodeData(
                    showId = show.id,
                    showName = show.name,
                    season = c.season,
                    title = name,
                    aired = airDate,
                    stillPath = still,
                    thumbnail = still?.let { "https://image.tmdb.org/t/p/w780$it" },
                    thumbnailOriginal = still?.let { "https://image.tmdb.org/t/p/original$it" }
                )
                CatalogCache.set(cacheKey, result, CatalogCache.Bucket.EPISODES)
                return result
            }
        }

        CatalogCache.set(cacheKey, null, CatalogCache.Bucket.EPISODES)
        return null
    }

    /**
     * Fast batch fetch of an entire TMDB season's episodes in ONE request (`/tv/{id}/season/{s}`),
     * avoiding N individual per-episode requests when loading a season's episode list on mobile.
     */
    suspend fun getSeasonEpisodesMap(
        animeTitle: String,
        seasonNumber: Int
    ): Map<Int, TmdbEpisodeData> {
        if (!isConfigured() || seasonNumber <= 0) return emptyMap()
        val show = searchShow(animeTitle) ?: return emptyMap()
        val cacheKey = "tmdb:season-eps:${show.id}:$seasonNumber"
        CatalogCache.get<Map<Int, TmdbEpisodeData>>(cacheKey)?.let { return it }

        val res = getJson("/tv/${show.id}/season/$seasonNumber") ?: return emptyMap()
        val arr = res.optJSONArray("episodes") ?: return emptyMap()
        val map = LinkedHashMap<Int, TmdbEpisodeData>()

        for (i in 0 until arr.length()) {
            val epObj = arr.optJSONObject(i) ?: continue
            val epNum = epObj.optInt("episode_number", -1)
            if (epNum <= 0) continue
            val name = epObj.optString("name").takeIf { it.isNotBlank() && it != "null" }
            val airDate = epObj.optString("air_date").takeIf { it.isNotBlank() && it != "null" }
            val still = epObj.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
            if (name != null || still != null) {
                map[epNum] = TmdbEpisodeData(
                    showId = show.id,
                    showName = show.name,
                    season = seasonNumber,
                    title = name,
                    aired = airDate,
                    stillPath = still,
                    thumbnail = still?.let { "https://image.tmdb.org/t/p/w780$it" },
                    thumbnailOriginal = still?.let { "https://image.tmdb.org/t/p/original$it" }
                )
            }
        }
        CatalogCache.set(cacheKey, map, CatalogCache.Bucket.EPISODES)
        return map
    }

    suspend fun getShowEpisodeCount(animeTitle: String): Int? {
        if (!isConfigured()) return null
        val cacheKey = "tmdb:epcount:${animeTitle.lowercase()}"
        when (val cached = CatalogCache.lookup<Int>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val show = searchShow(animeTitle)
        if (show == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.EPISODES)
            return null
        }
        val seasons = getShowSeasons(show.id)
        val count = seasons.sumOf { it.episodeCount.coerceAtLeast(0) }.takeIf { it > 0 }
        CatalogCache.set(cacheKey, count, CatalogCache.Bucket.EPISODES)
        return count
    }
}
