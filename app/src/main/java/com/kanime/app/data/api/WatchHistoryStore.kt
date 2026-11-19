package com.kanime.app.data.api

import android.content.Context
import android.content.SharedPreferences
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryEntry(
    val anime: Anime,
    val episodeNumber: Int,
    val episodeTitle: String,
    val episodeThumbnail: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isNearlyFinished: Boolean
        get() = durationMs > 60_000L && (progressFraction >= 0.95f || (durationMs - positionMs) <= 15_000L)

    fun toEpisode(): Episode = Episode(
        id = "${anime.id}_ep_$episodeNumber",
        episodeNumber = episodeNumber,
        title = episodeTitle.ifBlank { "Episode $episodeNumber" },
        duration = anime.duration.ifBlank { "24m" },
        thumbnail = episodeThumbnail.ifBlank { anime.bannerUrl.ifBlank { anime.coverUrl } },
    )
}

/** How far into one episode the viewer got. Used for progress bars, watched marks and "Continue Ep N". */
data class EpisodeProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    /** Past ~90% (credits) or less than 90s left of an episode that is at least a minute long. */
    val isWatched: Boolean
        get() = durationMs > 60_000L && (fraction >= 0.90f || (durationMs - positionMs) <= 90_000L)

    /** Started, but not finished. */
    val isInProgress: Boolean
        get() = !isWatched && positionMs >= 3_000L && durationMs > 0L

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)
}

/**
 * Persists user-saved Watchlist items (no default auto-favorites) and Watch History with exact
 * playback resume timestamps across app restarts.
 */
object WatchHistoryStore {
    private const val PREFS_NAME = "kanime_user_library_prefs"
    private const val KEY_WATCHLIST = "saved_watchlist_v1"
    private const val KEY_HISTORY = "watch_history_v1"
    private const val KEY_EPISODE_POSITIONS = "episode_positions_v1"
    private const val MAX_HISTORY_ITEMS = 60
    private const val MAX_EPISODE_POSITIONS = 250

    private var prefs: SharedPreferences? = null

    private val _watchlist = MutableStateFlow<List<Anime>>(emptyList())
    val watchlistFlow: StateFlow<List<Anime>> = _watchlist.asStateFlow()

    private val _watchlistIds = MutableStateFlow<Set<String>>(emptySet())
    val watchlistIdsFlow: StateFlow<Set<String>> = _watchlistIds.asStateFlow()

    private val _history = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val historyFlow: StateFlow<List<WatchHistoryEntry>> = _history.asStateFlow()

    // Key: "<animeId>:<episodeNumber>" -> Pair(positionMs, durationMs)
    private val episodePositions = LinkedHashMap<String, Pair<Long, Long>>()

    private val _episodeProgress = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())

    /** Per-episode progress for every tracked episode, keyed "<animeId>:<episodeNumber>". */
    val episodeProgressFlow: StateFlow<Map<String, EpisodeProgress>> = _episodeProgress.asStateFlow()

    /** Progress of every tracked episode of one anime, keyed by episode number. */
    fun progressByEpisode(all: Map<String, EpisodeProgress>, animeId: String): Map<Int, EpisodeProgress> {
        val prefix = "$animeId:"
        val result = HashMap<Int, EpisodeProgress>()
        for ((key, value) in all) {
            if (!key.startsWith(prefix)) continue
            val number = key.substring(prefix.length).toIntOrNull() ?: continue
            result[number] = value
        }
        return result
    }

    private fun publishProgress() {
        val snapshot = synchronized(episodePositions) {
            episodePositions.mapValues { (_, v) -> EpisodeProgress(v.first, v.second) }
        }
        _episodeProgress.value = snapshot
    }

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val savedWatchlist = decodeWatchlist(sp.getString(KEY_WATCHLIST, null))
        _watchlist.value = savedWatchlist
        _watchlistIds.value = savedWatchlist.map { it.id }.toSet()

        val savedHistory = decodeHistory(sp.getString(KEY_HISTORY, null))
        _history.value = savedHistory

        synchronized(episodePositions) {
            episodePositions.clear()
            episodePositions.putAll(decodeEpisodePositions(sp.getString(KEY_EPISODE_POSITIONS, null)))
            // Seed from history if not already present
            savedHistory.forEach { entry ->
                val key = positionKey(entry.anime.id, entry.episodeNumber)
                if (key !in episodePositions) {
                    episodePositions[key] = entry.positionMs to entry.durationMs
                }
            }
        }
        publishProgress()
    }

    fun toggleWatchlist(anime: Anime): Set<String> {
        val current = _watchlist.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == anime.id }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(0, anime)
        }
        _watchlist.value = current
        val ids = current.map { it.id }.toSet()
        _watchlistIds.value = ids
        prefs?.edit()?.putString(KEY_WATCHLIST, encodeWatchlist(current))?.apply()
        return ids
    }

    fun removeFromWatchlist(animeId: String): Set<String> {
        val updated = _watchlist.value.filterNot { it.id == animeId }
        _watchlist.value = updated
        val ids = updated.map { it.id }.toSet()
        _watchlistIds.value = ids
        prefs?.edit()?.putString(KEY_WATCHLIST, encodeWatchlist(updated))?.apply()
        return ids
    }

    fun recordProgress(
        anime: Anime,
        episode: Episode,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val safePos = positionMs.coerceAtLeast(0L)
        val safeDur = durationMs.coerceAtLeast(0L)
        val key = positionKey(anime.id, episode.episodeNumber)

        val positionsJson = synchronized(episodePositions) {
            val existing = episodePositions.remove(key)
            // Starting playback "touches" the entry with an unknown duration. Keep what we already know so
            // opening an episode never wipes its watched mark / progress bar.
            episodePositions[key] = if (safeDur == 0L && existing != null && existing.second > 0L) {
                existing
            } else {
                safePos to safeDur
            }
            while (episodePositions.size > MAX_EPISODE_POSITIONS) {
                val oldestKey = episodePositions.keys.firstOrNull() ?: break
                episodePositions.remove(oldestKey)
            }
            encodeEpisodePositions(episodePositions)
        }

        val entry = WatchHistoryEntry(
            anime = anime,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
            episodeThumbnail = episode.thumbnail.ifBlank { anime.bannerUrl.ifBlank { anime.coverUrl } },
            positionMs = safePos,
            durationMs = safeDur,
            watchedAtMs = nowMs,
        )
        val updatedHistory = buildList {
            add(entry)
            _history.value.forEach { existing ->
                if (existing.anime.id != anime.id && size < MAX_HISTORY_ITEMS) {
                    add(existing)
                }
            }
        }
        _history.value = updatedHistory
        prefs?.edit()
            ?.putString(KEY_HISTORY, encodeHistory(updatedHistory))
            ?.putString(KEY_EPISODE_POSITIONS, positionsJson)
            ?.apply()
        publishProgress()
    }

    /**
     * Returns the saved resume timestamp in milliseconds for (animeId, episodeNumber),
     * or 0L if unwatched or already near the end of credits.
     */
    fun getResumePositionMs(animeId: String, episodeNumber: Int): Long {
        val pair = synchronized(episodePositions) {
            episodePositions[positionKey(animeId, episodeNumber)]
        } ?: _history.value
            .firstOrNull { it.anime.id == animeId && it.episodeNumber == episodeNumber }
            ?.let { it.positionMs to it.durationMs }
            ?: return 0L

        val (pos, dur) = pair
        if (pos < 3_000L) return 0L
        if (dur > 60_000L && (pos.toFloat() / dur.toFloat() >= 0.95f || (dur - pos) <= 15_000L)) {
            return 0L
        }
        return pos
    }

    fun removeHistoryItem(animeId: String) {
        val updated = _history.value.filterNot { it.anime.id == animeId }
        _history.value = updated
        val positionsJson = synchronized(episodePositions) {
            val prefix = "$animeId:"
            episodePositions.keys.filter { it.startsWith(prefix) }.forEach { episodePositions.remove(it) }
            encodeEpisodePositions(episodePositions)
        }
        prefs?.edit()
            ?.putString(KEY_HISTORY, encodeHistory(updated))
            ?.putString(KEY_EPISODE_POSITIONS, positionsJson)
            ?.apply()
        publishProgress()
    }

    fun clearHistory() {
        _history.value = emptyList()
        synchronized(episodePositions) {
            episodePositions.clear()
        }
        prefs?.edit()
            ?.remove(KEY_HISTORY)
            ?.remove(KEY_EPISODE_POSITIONS)
            ?.apply()
        publishProgress()
    }

    private fun positionKey(animeId: String, episodeNumber: Int): String = "$animeId:$episodeNumber"

    internal fun encodeAnime(anime: Anime): JSONObject = JSONObject().apply {
        put("id", anime.id)
        put("title", anime.title)
        put("japaneseTitle", anime.japaneseTitle)
        put("synopsis", anime.synopsis)
        put("score", anime.score)
        put("episodes", anime.episodes)
        put("duration", anime.duration)
        put("status", anime.status)
        put("season", anime.season)
        put("year", anime.year)
        put("genres", JSONArray(anime.genres))
        put("studio", anime.studio)
        put("coverUrl", anime.coverUrl)
        put("bannerUrl", anime.bannerUrl)
        if (anime.malId != null) put("malId", anime.malId)
        if (anime.anilistId != null) put("anilistId", anime.anilistId)
        if (anime.kitsuId != null) put("kitsuId", anime.kitsuId)
        if (!anime.englishTitle.isNullOrBlank()) put("englishTitle", anime.englishTitle)
        if (!anime.romajiTitle.isNullOrBlank()) put("romajiTitle", anime.romajiTitle)
        if (!anime.logoUrl.isNullOrBlank()) put("logoUrl", anime.logoUrl)
        if (!anime.type.isNullOrBlank()) put("type", anime.type)
    }

    internal fun decodeAnime(jo: JSONObject): Anime {
        val genresArr = jo.optJSONArray("genres")
        val genres = buildList {
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optString(i)
                    if (g.isNotBlank()) add(g)
                }
            }
        }
        return Anime(
            id = jo.optString("id"),
            title = jo.optString("title"),
            japaneseTitle = jo.optString("japaneseTitle"),
            synopsis = jo.optString("synopsis"),
            score = jo.optDouble("score", 0.0),
            episodes = jo.optInt("episodes", 0),
            duration = jo.optString("duration", "24m"),
            status = jo.optString("status", "Unknown"),
            season = jo.optString("season", ""),
            year = jo.optInt("year", 0),
            genres = genres,
            studio = jo.optString("studio", ""),
            coverUrl = jo.optString("coverUrl"),
            bannerUrl = jo.optString("bannerUrl"),
            malId = if (jo.has("malId")) jo.optInt("malId").takeIf { it > 0 } else null,
            anilistId = if (jo.has("anilistId")) jo.optInt("anilistId").takeIf { it > 0 } else null,
            kitsuId = if (jo.has("kitsuId")) jo.optInt("kitsuId").takeIf { it > 0 } else null,
            englishTitle = jo.optString("englishTitle").takeIf { it.isNotBlank() },
            romajiTitle = jo.optString("romajiTitle").takeIf { it.isNotBlank() },
            logoUrl = jo.optString("logoUrl").takeIf { it.isNotBlank() },
            type = jo.optString("type").takeIf { it.isNotBlank() },
        )
    }

    internal fun encodeWatchlist(list: List<Anime>): String {
        val ja = JSONArray()
        list.forEach { ja.put(encodeAnime(it)) }
        return ja.toString()
    }

    internal fun decodeWatchlist(raw: String?): List<Anime> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val ja = JSONArray(raw)
            buildList {
                for (i in 0 until ja.length()) {
                    val jo = ja.optJSONObject(i) ?: continue
                    val anime = decodeAnime(jo)
                    if (anime.id.isNotBlank()) add(anime)
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun encodeHistory(list: List<WatchHistoryEntry>): String {
        val ja = JSONArray()
        list.forEach { entry ->
            ja.put(
                JSONObject().apply {
                    put("anime", encodeAnime(entry.anime))
                    put("episodeNumber", entry.episodeNumber)
                    put("episodeTitle", entry.episodeTitle)
                    put("episodeThumbnail", entry.episodeThumbnail)
                    put("positionMs", entry.positionMs)
                    put("durationMs", entry.durationMs)
                    put("watchedAtMs", entry.watchedAtMs)
                },
            )
        }
        return ja.toString()
    }

    internal fun decodeHistory(raw: String?): List<WatchHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val ja = JSONArray(raw)
            buildList {
                for (i in 0 until ja.length()) {
                    val jo = ja.optJSONObject(i) ?: continue
                    val animeObj = jo.optJSONObject("anime") ?: continue
                    val anime = decodeAnime(animeObj)
                    if (anime.id.isBlank()) continue
                    add(
                        WatchHistoryEntry(
                            anime = anime,
                            episodeNumber = jo.optInt("episodeNumber", 1),
                            episodeTitle = jo.optString("episodeTitle", "Episode 1"),
                            episodeThumbnail = jo.optString("episodeThumbnail", ""),
                            positionMs = jo.optLong("positionMs", 0L),
                            durationMs = jo.optLong("durationMs", 0L),
                            watchedAtMs = jo.optLong("watchedAtMs", 0L),
                        ),
                    )
                }
            }.sortedByDescending { it.watchedAtMs }
        }.getOrDefault(emptyList())
    }

    private fun encodeEpisodePositions(map: Map<String, Pair<Long, Long>>): String {
        val jo = JSONObject()
        map.forEach { (k, v) ->
            jo.put(k, JSONArray().put(v.first).put(v.second))
        }
        return jo.toString()
    }

    private fun decodeEpisodePositions(raw: String?): Map<String, Pair<Long, Long>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val jo = JSONObject(raw)
            val result = LinkedHashMap<String, Pair<Long, Long>>()
            val keys = jo.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = jo.optJSONArray(k) ?: continue
                if (arr.length() >= 2) {
                    result[k] = arr.optLong(0, 0L) to arr.optLong(1, 0L)
                }
            }
            result
        }.getOrDefault(emptyMap())
    }
}
