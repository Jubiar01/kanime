package com.kanime.app.data.api

import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Kitsu JSON:API client (`https://kitsu.app/api/edge`).
 *
 * Serves two roles:
 * 1. Anivault-API fallback art & episode-still source (MAL ID -> Kitsu mapping, poster/cover art, episode thumbnails/titles)
 * 2. Fallback catalog search/trending/top-airing if AniList is unavailable (preserving all existing KAnime methods)
 */
object KitsuAnimeApi {

    private const val BASE_URL = "https://kitsu.app/api/edge"

    data class KitsuAnimeImages(
        val kitsuAnimeId: Int,
        val canonicalTitle: String?,
        val poster: String?,
        val posterOriginal: String?,
        val cover: String?,
        val coverOriginal: String?
    )

    data class KitsuEpisodeData(
        val kitsuAnimeId: Int,
        val episodeId: Int,
        val episodeNumber: Int,
        val title: String?,
        val titleJapanese: String?,
        val aired: String?,
        val thumbnail: String?
    )

    private suspend fun getJson(urlString: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(urlString)
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "KAnime/1.0 (Android)")
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

    // ── Anivault-API Fallback Methods (MAL ID mapping, Art, Episode Stills) ──

    /**
     * Resolves a MAL ID (and/or title) to a Kitsu anime ID: MAL mapping first, title search fallback.
     */
    suspend fun getKitsuAnimeId(malId: Int?, animeTitle: String?): Int? {
        val cacheKey = if (malId != null) {
            "kitsu:id:mal:$malId"
        } else {
            "kitsu:id:title:${animeTitle.orEmpty().lowercase()}"
        }
        when (val cached = CatalogCache.lookup<Int>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        var kitsuAnimeId: Int? = null

        if (malId != null) {
            val mapUrl = "$BASE_URL/mappings".toHttpUrl().newBuilder()
                .addQueryParameter("filter[externalSite]", "myanimelist/anime")
                .addQueryParameter("filter[externalId]", malId.toString())
                .addQueryParameter("include", "item")
                .build()
                .toString()
            val mapRes = getJson(mapUrl)
            val included = mapRes?.optJSONArray("included")
            if (included != null) {
                for (i in 0 until included.length()) {
                    val item = included.optJSONObject(i) ?: continue
                    if (item.optString("type") == "anime") {
                        kitsuAnimeId = item.optString("id").toIntOrNull()
                        if (kitsuAnimeId != null) break
                    }
                }
            }
        }

        if (kitsuAnimeId == null && !animeTitle.isNullOrBlank()) {
            val searchUrl = "$BASE_URL/anime".toHttpUrl().newBuilder()
                .addQueryParameter("filter[text]", animeTitle)
                .addQueryParameter("page[limit]", "3")
                .build()
                .toString()
            val srchRes = getJson(searchUrl)
            kitsuAnimeId = srchRes?.optJSONArray("data")?.optJSONObject(0)?.optString("id")?.toIntOrNull()
        }

        CatalogCache.set(cacheKey, kitsuAnimeId, CatalogCache.Bucket.MAPPING)
        return kitsuAnimeId
    }

    suspend fun getAnimeImages(kitsuAnimeId: Int): KitsuAnimeImages? {
        val cacheKey = "kitsu:images:$kitsuAnimeId"
        when (val cached = CatalogCache.lookup<KitsuAnimeImages>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val animeRes = getJson("$BASE_URL/anime/$kitsuAnimeId")
        val attrs = animeRes?.optJSONObject("data")?.optJSONObject("attributes")
        if (attrs == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
            return null
        }

        val posterImg = attrs.optJSONObject("posterImage")
        val coverImg = attrs.optJSONObject("coverImage")

        fun pickUrl(obj: JSONObject?, vararg keys: String): String? {
            if (obj == null) return null
            for (k in keys) {
                val v = obj.optString(k).takeIf { it.isNotBlank() && it != "null" }
                if (v != null) return v
            }
            return null
        }

        val poster = pickUrl(posterImg, "large", "medium", "original")
        val posterOriginal = pickUrl(posterImg, "original") ?: poster
        val cover = pickUrl(coverImg, "large", "original")
        val coverOriginal = pickUrl(coverImg, "original") ?: cover

        if (poster == null && cover == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
            return null
        }

        val result = KitsuAnimeImages(
            kitsuAnimeId = kitsuAnimeId,
            canonicalTitle = attrs.optString("canonicalTitle").takeIf { it.isNotBlank() && it != "null" },
            poster = poster,
            posterOriginal = posterOriginal,
            cover = cover,
            coverOriginal = coverOriginal
        )
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }

    private fun parseEpisodeObject(kitsuAnimeId: Int, epObj: JSONObject): KitsuEpisodeData? {
        val epId = epObj.optString("id").toIntOrNull() ?: return null
        val attrs = epObj.optJSONObject("attributes") ?: return null
        val number = attrs.optInt("number", -1)
        val titlesObj = attrs.optJSONObject("titles")
        val title = attrs.optString("canonicalTitle").takeIf { it.isNotBlank() && it != "null" }
            ?: titlesObj?.optString("en")?.takeIf { it.isNotBlank() && it != "null" }
            ?: titlesObj?.optString("en_us")?.takeIf { it.isNotBlank() && it != "null" }
        val titleJapanese = titlesObj?.optString("ja_jp")?.takeIf { it.isNotBlank() && it != "null" }
        val aired = attrs.optString("airdate").takeIf { it.isNotBlank() && it != "null" }

        val thumbObj = attrs.optJSONObject("thumbnail")
        var thumbnail: String? = null
        if (thumbObj != null) {
            for (size in listOf("original", "large", "medium", "small", "tiny")) {
                val u = thumbObj.optString(size).takeIf { it.isNotBlank() && it != "null" }
                if (u != null) {
                    thumbnail = u
                    break
                }
            }
        }
        if (title == null && thumbnail == null) return null

        return KitsuEpisodeData(
            kitsuAnimeId = kitsuAnimeId,
            episodeId = epId,
            episodeNumber = number,
            title = title,
            titleJapanese = titleJapanese,
            aired = aired,
            thumbnail = thumbnail
        )
    }

    suspend fun getEpisodeData(kitsuAnimeId: Int, epNum: Int): KitsuEpisodeData? {
        val cacheKey = "kitsu:epdata:$kitsuAnimeId:$epNum"
        when (val cached = CatalogCache.lookup<KitsuEpisodeData>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val url = "$BASE_URL/anime/$kitsuAnimeId/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("filter[number]", epNum.toString())
            .addQueryParameter("page[limit]", "1")
            .build()
            .toString()
        val epRes = getJson(url)
        val epObj = epRes?.optJSONArray("data")?.optJSONObject(0)
        val parsed = epObj?.let { parseEpisodeObject(kitsuAnimeId, it) }
        CatalogCache.set(cacheKey, parsed, CatalogCache.Bucket.EPISODES)
        return parsed
    }

    /**
     * Batch-fetches up to [maxEpisodes] episode records from Kitsu in pages of 20 (Kitsu's max page size),
     * dramatically reducing mobile network round-trips when enriching episode thumbnails.
     */
    suspend fun getEpisodesBatch(kitsuAnimeId: Int, maxEpisodes: Int = 60): Map<Int, KitsuEpisodeData> {
        val cacheKey = "kitsu:epbatch:$kitsuAnimeId:$maxEpisodes"
        CatalogCache.get<Map<Int, KitsuEpisodeData>>(cacheKey)?.let { return it }

        val result = LinkedHashMap<Int, KitsuEpisodeData>()
        var offset = 0
        val pageSize = 20

        while (offset < maxEpisodes) {
            val url = "$BASE_URL/anime/$kitsuAnimeId/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("page[limit]", pageSize.toString())
                .addQueryParameter("page[offset]", offset.toString())
                .addQueryParameter("sort", "number")
                .build()
                .toString()
            val res = getJson(url) ?: break
            val arr = res.optJSONArray("data") ?: break
            if (arr.length() == 0) break

            for (i in 0 until arr.length()) {
                val epObj = arr.optJSONObject(i) ?: continue
                val parsed = parseEpisodeObject(kitsuAnimeId, epObj) ?: continue
                if (parsed.episodeNumber > 0) {
                    result[parsed.episodeNumber] = parsed
                    CatalogCache.set("kitsu:epdata:$kitsuAnimeId:${parsed.episodeNumber}", parsed, CatalogCache.Bucket.EPISODES)
                }
            }
            if (arr.length() < pageSize) break
            offset += pageSize
        }

        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.EPISODES)
        return result
    }

    // ── Legacy / Fallback Catalog Endpoints (kept 100% compatible) ───────────

    suspend fun searchAnime(query: String, limit: Int = 20): List<Anime> {
        val encodedQuery = withContext(Dispatchers.IO) { URLEncoder.encode(query.trim(), "UTF-8") }
        val endpoint = "$BASE_URL/anime?filter[text]=$encodedQuery&page[limit]=$limit"
        return fetchAnimeList(endpoint)
    }

    suspend fun getTrending(limit: Int = 15): List<Anime> {
        val endpoint = "$BASE_URL/trending/anime?limit=$limit"
        return fetchAnimeList(endpoint)
    }

    suspend fun getTopAiring(limit: Int = 15): List<Anime> {
        val endpoint = "$BASE_URL/anime?filter[status]=current&sort=-userCount&page[limit]=$limit"
        return fetchAnimeList(endpoint)
    }

    suspend fun getByCategory(category: String, limit: Int = 15): List<Anime> {
        val encodedCat = withContext(Dispatchers.IO) { URLEncoder.encode(category.lowercase(), "UTF-8") }
        val endpoint = "$BASE_URL/anime?filter[categories]=$encodedCat&sort=-userCount&page[limit]=$limit"
        return fetchAnimeList(endpoint)
    }

    private suspend fun fetchAnimeList(urlString: String): List<Anime> {
        val root = getJson(urlString) ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        val result = mutableListOf<Anime>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            parseKitsuItem(item)?.let { result.add(it) }
        }
        return result
    }

    private fun parseKitsuItem(item: JSONObject): Anime? {
        return try {
            val id = item.optString("id", "")
            if (id.isBlank()) return null

            val attrs = item.optJSONObject("attributes") ?: return null
            val titles = attrs.optJSONObject("titles") ?: JSONObject()

            val canonicalTitle = attrs.optString("canonicalTitle", "")
            val enTitle = titles.optString("en", "")
            val enJpTitle = titles.optString("en_jp", "")
            val jaJpTitle = titles.optString("ja_jp", "")

            val title = when {
                enTitle.isNotBlank() -> enTitle
                canonicalTitle.isNotBlank() -> canonicalTitle
                enJpTitle.isNotBlank() -> enJpTitle
                else -> "Anime #$id"
            }

            val japaneseTitle = when {
                jaJpTitle.isNotBlank() -> jaJpTitle
                enJpTitle.isNotBlank() -> enJpTitle
                else -> canonicalTitle
            }

            val synopsis = attrs.optString("synopsis", "No synopsis provided.").let {
                if (it.isBlank()) "No synopsis provided." else it
            }

            val rawRating = attrs.optString("averageRating", "80.0").toDoubleOrNull() ?: 80.0
            val score = Math.round((rawRating / 10.0) * 10.0) / 10.0

            val rawEpisodes = attrs.optInt("episodeCount", 12)
            val episodes = if (rawEpisodes > 0) rawEpisodes else 12

            val durationMin = attrs.optInt("episodeLength", 24).takeIf { it > 0 } ?: 24
            val duration = "${durationMin}m"

            val rawStatus = attrs.optString("status", "finished")
            val status = when (rawStatus) {
                "current" -> "Airing"
                "unreleased", "upcoming" -> "Upcoming"
                else -> "Completed"
            }

            val startDate = attrs.optString("startDate", "")
            val year = if (startDate.length >= 4) startDate.substring(0, 4).toIntOrNull() ?: 2024 else 2024
            val season = attrs.optString("subtype", "TV").uppercase()

            val posterObj = attrs.optJSONObject("posterImage")
            val coverUrl = posterObj?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
                ?: posterObj?.optString("medium")?.takeIf { it.isNotBlank() && it != "null" }
                ?: posterObj?.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=60"

            val coverObj = attrs.optJSONObject("coverImage")
            val bannerUrl = coverObj?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
                ?: coverObj?.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                ?: coverUrl

            val showType = attrs.optString("showType", "TV")
            val ageRating = attrs.optString("ageRating", "PG")
            val genres = listOf(showType, ageRating, "Anime").filter { it.isNotBlank() }

            val epCountForList = episodes.coerceIn(1, 36)
            val episodeList = (1..epCountForList).map { epNum ->
                Episode(
                    id = "kitsu_${id}_ep_$epNum",
                    episodeNumber = epNum,
                    title = "$title - Episode $epNum",
                    duration = duration,
                    thumbnail = bannerUrl,
                    description = "Episode $epNum of $title"
                )
            }

            Anime(
                id = "kitsu_$id",
                title = title,
                japaneseTitle = japaneseTitle,
                synopsis = synopsis,
                score = score,
                episodes = episodes,
                duration = duration,
                status = status,
                season = season,
                year = year,
                genres = genres,
                studio = "Animation Studio",
                coverUrl = coverUrl,
                bannerUrl = bannerUrl,
                episodeList = episodeList,
                isTrending = true,
                isTopAiring = (status == "Airing"),
                isSeasonal = true,
                kitsuId = id.toIntOrNull(),
                englishTitle = enTitle.takeIf { it.isNotBlank() },
                romajiTitle = enJpTitle.takeIf { it.isNotBlank() } ?: canonicalTitle.takeIf { it.isNotBlank() },
                posterSource = "kitsu",
                bannerSource = "kitsu"
            )
        } catch (_: Exception) {
            null
        }
    }
}
