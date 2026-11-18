package com.kanime.app.data.api

import android.util.Log
import com.kanime.app.data.model.Anime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToLong

data class SkipSegment(
    val type: String, // "op", "mixed-op", "recap", "ed", "mixed-ed"
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val sourceEpisodeLengthSec: Double = 0.0,
) {
    /**
     * Shows slightly early (1.5s lead-in) right as the intro/recap starts, and hides cleanly
     * 1.5s before the segment finishes so it never pops up or lingers at the end.
     */
    fun isActiveAt(positionMs: Long): Boolean {
        if (endMs <= startMs + 2_000L) return false
        val showFromMs = (startMs - LEAD_IN_MS).coerceAtLeast(0L)
        val hideAtMs = (endMs - TAIL_HIDE_MS).coerceAtLeast(showFromMs + 1_000L)
        return positionMs in showFromMs until hideAtMs
    }

    private companion object {
        const val LEAD_IN_MS = 1_500L
        const val TAIL_HIDE_MS = 1_500L
    }
}

data class EpisodeSkipTimes(
    val intro: SkipSegment? = null,
    val recap: SkipSegment? = null,
    val outro: SkipSegment? = null,
) {
    val isEmpty: Boolean
        get() = intro == null && recap == null && outro == null

    fun activeSegmentAt(positionMs: Long): SkipSegment? {
        // Prefer recap first when recap runs right before the intro; avoid early intro lead-in
        // preempting an active recap.
        if (recap != null && positionMs >= (recap.startMs - 1_500L).coerceAtLeast(0L) && positionMs < recap.endMs - 1_500L) {
            return recap
        }
        if (intro?.isActiveAt(positionMs) == true) return intro
        if (outro?.isActiveAt(positionMs) == true) return outro
        return null
    }
}

/**
 * Fetches community-verified anime Intro (OP), Recap, and Outro (ED) timestamps from the
 * public AniSkip v2 API matched to the exact streaming episode length so intro/recap prompts
 * trigger right when the intro/recap starts (not misaligned to another release cut).
 */
object AniSkipApi {
    private const val TAG = "AniSkipApi"
    private const val BASE_URL = "https://api.aniskip.com/v2/skip-times"
    private const val MAX_FALLBACK_LENGTH_DRIFT_SEC = 25.0

    private val cache = ConcurrentHashMap<String, EpisodeSkipTimes>()
    private val resolvedMalIdCache = ConcurrentHashMap<String, Int>()

    suspend fun getSkipTimes(
        anime: Anime,
        episodeNumber: Int,
        episodeLengthSec: Double = 0.0,
    ): EpisodeSkipTimes = withContext(Dispatchers.IO) {
        if (episodeNumber <= 0) return@withContext EpisodeSkipTimes()
        val malId = resolveMalId(anime) ?: return@withContext EpisodeSkipTimes()
        val hasExactLength = episodeLengthSec > 60.0
        val lengthBucket = if (hasExactLength) (episodeLengthSec / 5.0).toInt() else 0
        val cacheKey = "$malId:$episodeNumber:$lengthBucket"
        cache[cacheKey]?.let { return@withContext it }

        // 1) Query AniSkip with the exact stream duration first so the server matches our exact video cut
        var resolved = if (hasExactLength) {
            fetchAndParse(malId, episodeNumber, queryLengthSec = episodeLengthSec, actualLengthSec = episodeLengthSec)
        } else {
            EpisodeSkipTimes()
        }

        // 2) If exact-length query had no match, fall back to episodeLength=0 and validate length proximity
        if (resolved.isEmpty) {
            resolved = fetchAndParse(malId, episodeNumber, queryLengthSec = 0.0, actualLengthSec = episodeLengthSec)
        }

        Log.d(
            TAG,
            "malId=$malId ep=$episodeNumber (streamDur=${episodeLengthSec.toInt()}s) -> " +
                "recap=${resolved.recap?.let { "${it.startMs / 1000}s..${it.endMs / 1000}s" }}, " +
                "intro=${resolved.intro?.let { "${it.startMs / 1000}s..${it.endMs / 1000}s" }}, " +
                "outro=${resolved.outro?.let { "${it.startMs / 1000}s..${it.endMs / 1000}s" }}",
        )

        cache[cacheKey] = resolved
        resolved
    }

    private fun fetchAndParse(
        malId: Int,
        episodeNumber: Int,
        queryLengthSec: Double,
        actualLengthSec: Double,
    ): EpisodeSkipTimes {
        val lengthParam = if (queryLengthSec > 60.0) {
            String.format(Locale.US, "%.3f", queryLengthSec)
        } else {
            "0"
        }
        val url = "$BASE_URL/$malId/$episodeNumber" +
            "?types[]=op&types[]=mixed-op&types[]=recap&types[]=ed&types[]=mixed-ed" +
            "&episodeLength=$lengthParam"

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", CatalogConfig.DESKTOP_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            CatalogConfig.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching EpisodeSkipTimes()
                }
                val body = response.body.string()
                parseResponse(body, actualEpisodeLengthSec = actualLengthSec)
            }
        }.onFailure { error ->
            Log.w(TAG, "AniSkip lookup failed for malId=$malId ep=$episodeNumber (len=$lengthParam): ${error.message}")
        }.getOrDefault(EpisodeSkipTimes())
    }

    internal fun parseResponse(
        jsonBody: String,
        actualEpisodeLengthSec: Double = 0.0,
    ): EpisodeSkipTimes {
        if (jsonBody.isBlank()) return EpisodeSkipTimes()
        val root = JSONObject(jsonBody)
        if (!root.optBoolean("found", false)) return EpisodeSkipTimes()
        val results = root.optJSONArray("results") ?: return EpisodeSkipTimes()

        val introCandidates = mutableListOf<SkipSegment>()
        val recapCandidates = mutableListOf<SkipSegment>()
        val outroCandidates = mutableListOf<SkipSegment>()

        val hasActualLength = actualEpisodeLengthSec > 60.0
        val maxEndMs = if (hasActualLength) (actualEpisodeLengthSec * 1000.0).roundToLong() else Long.MAX_VALUE

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val skipType = item.optString("skipType", "").lowercase()
            val interval = item.optJSONObject("interval") ?: continue
            val startSec = interval.optDouble("startTime", -1.0)
            val endSec = interval.optDouble("endTime", -1.0)
            val itemEpLen = item.optDouble("episodeLength", 0.0)
            if (startSec < 0.0 || endSec <= startSec + 2.0) continue

            // Filter out entries from a completely different release/cut when stream duration is known
            if (hasActualLength && itemEpLen > 60.0 && abs(itemEpLen - actualEpisodeLengthSec) > MAX_FALLBACK_LENGTH_DRIFT_SEC) {
                continue
            }

            val startMs = (startSec * 1000.0).roundToLong()
            val endMs = (endSec * 1000.0).roundToLong().coerceAtMost(maxEndMs)
            if (endMs <= startMs + 2_000L) continue

            when (skipType) {
                "op", "mixed-op" -> introCandidates += SkipSegment(skipType, "Skip Intro", startMs, endMs, itemEpLen)
                "recap" -> recapCandidates += SkipSegment("recap", "Skip Recap", startMs, endMs, itemEpLen)
                "ed", "mixed-ed" -> outroCandidates += SkipSegment(skipType, "Skip Outro", startMs, endMs, itemEpLen)
            }
        }

        fun pickBest(candidates: List<SkipSegment>, primaryType: String): SkipSegment? {
            if (candidates.isEmpty()) return null
            if (!hasActualLength) {
                return candidates.firstOrNull { it.type == primaryType } ?: candidates.first()
            }
            return candidates.minWithOrNull(
                compareBy<SkipSegment> { seg ->
                    if (seg.sourceEpisodeLengthSec > 60.0) abs(seg.sourceEpisodeLengthSec - actualEpisodeLengthSec) else 999.0
                }.thenBy { seg ->
                    if (seg.type == primaryType) 0 else 1
                },
            )
        }

        return EpisodeSkipTimes(
            intro = pickBest(introCandidates, "op"),
            recap = pickBest(recapCandidates, "recap"),
            outro = pickBest(outroCandidates, "ed"),
        )
    }

    private suspend fun resolveMalId(anime: Anime): Int? {
        anime.malId?.takeIf { it > 0 }?.let { return it }
        Regex("^mal_(\\d+)$").find(anime.id)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        resolvedMalIdCache[anime.id]?.let { return it }

        val query = (anime.romajiTitle ?: anime.title).trim()
        if (query.isBlank()) return null
        val found = runCatching {
            AniListApi.searchAnime(query, perPage = 3).firstOrNull { it.idMal != null }?.idMal
        }.getOrNull()
        if (found != null && found > 0) {
            resolvedMalIdCache[anime.id] = found
        }
        return found
    }
}
