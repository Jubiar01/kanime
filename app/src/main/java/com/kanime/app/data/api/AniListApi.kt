package com.kanime.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * AniList GraphQL API client, ported from SH0MIK/Anivault-API (src/scrapers/anilist.ts & src/utils/mapper.ts)
 * plus catalog discovery queries (trending, top airing, current season, genre/status browse, search).
 *
 * Used for:
 * - ID mapping (MAL ID <-> AniList ID)
 * - Season lists & catalog browsing
 * - Banner art (top banners + per-title banner/cover fallback)
 * - Streaming-episode titles/thumbnails (last-resort fallback)
 */
object AniListApi {

    private const val GRAPHQL_URL = "https://graphql.anilist.co"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    data class AniListMedia(
        val id: Int,
        val idMal: Int?,
        val titleRomaji: String?,
        val titleEnglish: String?,
        val titleNative: String?,
        val description: String?,
        val bannerImage: String?,
        val coverLarge: String?,
        val coverExtraLarge: String?,
        val genres: List<String>,
        val episodes: Int?,
        val duration: Int?,
        val averageScore: Int?,
        val format: String?,
        val status: String?,
        val season: String?,
        val seasonYear: Int?,
        val studio: String?
    )

    /** One anime in a prequel/sequel chain (keyless AniList relations graph). */
    data class AniListFranchiseEntry(
        val anilistId: Int,
        val malId: Int?,
        val title: String,
        val cover: String?,
        val format: String?,
        val episodes: Int?,
        val year: Int?
    )

    private data class FranchiseNode(
        val entry: AniListFranchiseEntry,
        val prequels: List<Pair<Int, String?>>,
        val sequels: List<Pair<Int, String?>>
    )

    data class AniListSeasonResult(
        val season: String,
        val seasonYear: Int,
        val media: List<AniListMedia>
    )

    data class AniListStreamingEpisode(
        val title: String?,
        val thumbnail: String?,
        val url: String?,
        val site: String?
    )

    data class AniListAnimeImages(
        val idMal: Int,
        val title: String?,
        val poster: String?,
        val posterOriginal: String?,
        val cover: String?,
        val coverOriginal: String?
    )

    private const val MEDIA_FIELDS = """
        id
        idMal
        title { romaji english native }
        description(asHtml: false)
        bannerImage
        coverImage { large extraLarge }
        genres
        episodes
        duration
        averageScore
        format
        status
        season
        seasonYear
        studios(isMain: true) { nodes { name } }
    """

    private suspend fun executeGraphQl(query: String, variables: JSONObject = JSONObject()): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("query", query)
                    put("variables", variables)
                }
                val request = Request.Builder()
                    .url(GRAPHQL_URL)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", CatalogConfig.DESKTOP_USER_AGENT)
                    .header("Origin", "https://anilist.co")
                    .header("Referer", "https://anilist.co/")
                    .build()

                CatalogConfig.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body.string()
                    val root = JSONObject(body)
                    if (root.has("errors") && (root.optJSONArray("errors")?.length() ?: 0) > 0) {
                        return@withContext null
                    }
                    root.optJSONObject("data")
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun parseMedia(obj: JSONObject): AniListMedia {
        val titleObj = obj.optJSONObject("title")
        val coverObj = obj.optJSONObject("coverImage")
        val genresArr = obj.optJSONArray("genres")
        val genres = mutableListOf<String>()
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                val g = genresArr.optString(i).trim()
                if (g.isNotBlank()) genres.add(g)
            }
        }
        val studioName = obj.optJSONObject("studios")
            ?.optJSONArray("nodes")
            ?.optJSONObject(0)
            ?.optString("name")
            ?.takeIf { it.isNotBlank() }

        val rawDesc = obj.optString("description", "").takeIf { it.isNotBlank() && it != "null" }
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.replace("&#39;", "'")
            ?.trim()

        return AniListMedia(
            id = obj.optInt("id", 0),
            idMal = if (obj.isNull("idMal")) null else obj.optInt("idMal").takeIf { it > 0 },
            titleRomaji = titleObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" },
            titleEnglish = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" },
            titleNative = titleObj?.optString("native")?.takeIf { it.isNotBlank() && it != "null" },
            description = rawDesc,
            bannerImage = obj.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" },
            coverLarge = coverObj?.optString("large")?.takeIf { it.isNotBlank() && it != "null" },
            coverExtraLarge = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() && it != "null" },
            genres = genres,
            episodes = if (obj.isNull("episodes")) null else obj.optInt("episodes").takeIf { it > 0 },
            duration = if (obj.isNull("duration")) null else obj.optInt("duration").takeIf { it > 0 },
            averageScore = if (obj.isNull("averageScore")) null else obj.optInt("averageScore").takeIf { it > 0 },
            format = obj.optString("format").takeIf { it.isNotBlank() && it != "null" },
            status = obj.optString("status").takeIf { it.isNotBlank() && it != "null" },
            season = obj.optString("season").takeIf { it.isNotBlank() && it != "null" },
            seasonYear = if (obj.isNull("seasonYear")) null else obj.optInt("seasonYear").takeIf { it > 0 },
            studio = studioName
        )
    }

    private fun parseMediaList(data: JSONObject?): List<AniListMedia> {
        val mediaArr = data?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        val list = mutableListOf<AniListMedia>()
        for (i in 0 until mediaArr.length()) {
            val item = mediaArr.optJSONObject(i) ?: continue
            val parsed = parseMedia(item)
            if (parsed.id > 0) list.add(parsed)
        }
        return list
    }

    fun currentSeason(): Pair<String, Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val season = when {
            month <= 3 -> "WINTER"
            month <= 6 -> "SPRING"
            month <= 9 -> "SUMMER"
            else -> "FALL"
        }
        return season to year
    }

    // ── Season Now ───────────────────────────────────────────────────────────

    suspend fun getSeasonNow(perPage: Int = 30): AniListSeasonResult {
        val (season, seasonYear) = currentSeason()
        val cacheKey = "anilist:season:$season:$seasonYear:$perPage"
        CatalogCache.get<List<AniListMedia>>(cacheKey)?.let {
            return AniListSeasonResult(season, seasonYear, it)
        }

        val query = """
            query (${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(season: ${'$'}season, seasonYear: ${'$'}seasonYear, type: ANIME, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("season", season)
            put("seasonYear", seasonYear)
            put("perPage", perPage)
        }
        val media = parseMediaList(executeGraphQl(query, vars))
        if (media.isNotEmpty()) {
            CatalogCache.set(cacheKey, media, CatalogCache.Bucket.EPISODES)
        }
        return AniListSeasonResult(season, seasonYear, media)
    }

    // ── Trending & Top Airing & Browse Catalog ───────────────────────────────

    suspend fun getTrending(page: Int = 1, perPage: Int = 20): List<AniListMedia> {
        val cacheKey = "anilist:trending:$page:$perPage"
        CatalogCache.get<List<AniListMedia>>(cacheKey)?.let { return it }

        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
        }
        val media = parseMediaList(executeGraphQl(query, vars))
        if (media.isNotEmpty()) {
            CatalogCache.set(cacheKey, media, CatalogCache.Bucket.EPISODES)
        }
        return media
    }

    suspend fun getTopAiring(page: Int = 1, perPage: Int = 20): List<AniListMedia> {
        val cacheKey = "anilist:airing:$page:$perPage"
        CatalogCache.get<List<AniListMedia>>(cacheKey)?.let { return it }

        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
        }
        val media = parseMediaList(executeGraphQl(query, vars))
        if (media.isNotEmpty()) {
            CatalogCache.set(cacheKey, media, CatalogCache.Bucket.EPISODES)
        }
        return media
    }

    suspend fun browseCatalog(
        genre: String? = null,
        status: String? = null,
        page: Int = 1,
        perPage: Int = 30
    ): List<AniListMedia> {
        val normGenre = genre?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
        val aniStatus = when (status?.lowercase()) {
            "airing" -> "RELEASING"
            "completed" -> "FINISHED"
            "upcoming" -> "NOT_YET_RELEASED"
            else -> null
        }

        val cacheKey = "anilist:browse:${normGenre ?: "all"}:${aniStatus ?: "all"}:$page:$perPage"
        CatalogCache.get<List<AniListMedia>>(cacheKey)?.let { return it }

        val varDecls = mutableListOf("${'$'}page: Int", "${'$'}perPage: Int")
        val args = mutableListOf("type: ANIME", "sort: POPULARITY_DESC", "isAdult: false")
        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
        }
        if (normGenre != null) {
            varDecls.add("${'$'}genre: String")
            args.add("genre: ${'$'}genre")
            vars.put("genre", normGenre)
        }
        if (aniStatus != null) {
            varDecls.add("${'$'}status: MediaStatus")
            args.add("status: ${'$'}status")
            vars.put("status", aniStatus)
        }

        val query = """
            query (${varDecls.joinToString(", ")}) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(${args.joinToString(", ")}) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val media = parseMediaList(executeGraphQl(query, vars))
        if (media.isNotEmpty()) {
            CatalogCache.set(cacheKey, media, CatalogCache.Bucket.EPISODES)
        }
        return media
    }

    // ── Search ───────────────────────────────────────────────────────────────

    suspend fun searchAnime(queryText: String, perPage: Int = 20): List<AniListMedia> {
        val q = queryText.trim()
        if (q.isBlank()) return emptyList()
        val cacheKey = "anilist:search:${q.lowercase()}:$perPage"
        CatalogCache.get<List<AniListMedia>>(cacheKey)?.let { return it }

        val query = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("search", q)
            put("perPage", perPage)
        }
        val media = parseMediaList(executeGraphQl(query, vars))
        if (media.isNotEmpty()) {
            CatalogCache.set(cacheKey, media, CatalogCache.Bucket.EPISODES)
        }
        return media
    }

    // ── ID Mapping (MAL ID -> AniList ID / Media) ────────────────────────────

    suspend fun malToAnilist(malId: Int): Int? {
        val cacheKey = "mal2al:$malId"
        when (val cached = CatalogCache.lookup<Int>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val query = """
            query (${'$'}malId: Int) {
              Media(idMal: ${'$'}malId, type: ANIME) { id idMal }
            }
        """.trimIndent()
        val vars = JSONObject().apply { put("malId", malId) }
        val id = executeGraphQl(query, vars)?.optJSONObject("Media")?.optInt("id")?.takeIf { it > 0 }
        CatalogCache.set(cacheKey, id, CatalogCache.Bucket.MAPPING)
        return id
    }

    suspend fun getMediaByMalId(malId: Int): AniListMedia? {
        val cacheKey = "anilist:media:mal:$malId"
        when (val cached = CatalogCache.lookup<AniListMedia>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val query = """
            query (${'$'}malId: Int) {
              Media(idMal: ${'$'}malId, type: ANIME) {
                $MEDIA_FIELDS
              }
            }
        """.trimIndent()
        val vars = JSONObject().apply { put("malId", malId) }
        val mediaObj = executeGraphQl(query, vars)?.optJSONObject("Media")
        val media = mediaObj?.let { parseMedia(it) }?.takeIf { it.id > 0 }
        CatalogCache.set(cacheKey, media, CatalogCache.Bucket.MAPPING)
        return media
    }

    // ── Franchise chain (prequel -> sequel) for the Seasons row ──────────────

    private const val FRANCHISE_FIELDS = """
        id
        idMal
        title { romaji english }
        coverImage { large extraLarge }
        format
        episodes
        seasonYear
        relations { edges { relationType node { id type format } } }
    """

    private suspend fun fetchFranchiseNode(argName: String, value: Int): FranchiseNode? {
        val query = "query (\$v: Int) { Media($argName: \$v, type: ANIME) { $FRANCHISE_FIELDS } }"
        val vars = JSONObject().apply { put("v", value) }
        val media = executeGraphQl(query, vars)?.optJSONObject("Media") ?: return null
        val id = media.optInt("id").takeIf { it > 0 } ?: return null

        val titleObj = media.optJSONObject("title")
        val title = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
            ?: titleObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
            ?: "Unknown"
        val coverObj = media.optJSONObject("coverImage")
        val cover = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() && it != "null" }
            ?: coverObj?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }

        val entry = AniListFranchiseEntry(
            anilistId = id,
            malId = if (media.isNull("idMal")) null else media.optInt("idMal").takeIf { it > 0 },
            title = title,
            cover = cover,
            format = media.optString("format").takeIf { it.isNotBlank() && it != "null" },
            episodes = if (media.isNull("episodes")) null else media.optInt("episodes").takeIf { it > 0 },
            year = if (media.isNull("seasonYear")) null else media.optInt("seasonYear").takeIf { it > 0 }
        )

        val prequels = ArrayList<Pair<Int, String?>>()
        val sequels = ArrayList<Pair<Int, String?>>()
        val edges = media.optJSONObject("relations")?.optJSONArray("edges")
        if (edges != null) {
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                if (node.optString("type") != "ANIME") continue
                val nodeId = node.optInt("id").takeIf { it > 0 } ?: continue
                val nodeFormat = node.optString("format").takeIf { it.isNotBlank() && it != "null" }
                when (edge.optString("relationType")) {
                    "PREQUEL" -> prequels.add(nodeId to nodeFormat)
                    "SEQUEL" -> sequels.add(nodeId to nodeFormat)
                }
            }
        }
        return FranchiseNode(entry, prequels, sequels)
    }

    /** Prefer a main-line show (TV / short / ONA) over movies, OVAs and specials when a node has several links. */
    private fun pickFranchiseLink(links: List<Pair<Int, String?>>, visited: Set<Int>): Int? {
        val open = links.filter { it.first !in visited }
        if (open.isEmpty()) return null
        return (open.firstOrNull { it.second == "TV" || it.second == "TV_SHORT" || it.second == "ONA" } ?: open.first()).first
    }

    /**
     * The whole prequel -> sequel chain this anime belongs to, oldest first (including this anime).
     * Keyless; one small AniList request per hop, cached for 24h.
     */
    suspend fun getFranchise(malId: Int): List<AniListFranchiseEntry> {
        val cacheKey = "anilist:franchise:v1:mal:$malId"
        CatalogCache.get<List<AniListFranchiseEntry>>(cacheKey)?.let { return it }

        val start = fetchFranchiseNode("idMal", malId) ?: return emptyList()
        val visited = mutableSetOf(start.entry.anilistId)

        val before = ArrayList<AniListFranchiseEntry>()
        var cursor = start
        var hops = 0
        while (hops < 8) {
            val prevId = pickFranchiseLink(cursor.prequels, visited) ?: break
            val prev = fetchFranchiseNode("id", prevId) ?: break
            visited += prev.entry.anilistId
            before.add(0, prev.entry)
            cursor = prev
            hops++
        }

        val chain = ArrayList<AniListFranchiseEntry>(before)
        chain += start.entry
        cursor = start
        hops = 0
        while (hops < 12) {
            val nextId = pickFranchiseLink(cursor.sequels, visited) ?: break
            val next = fetchFranchiseNode("id", nextId) ?: break
            visited += next.entry.anilistId
            chain += next.entry
            cursor = next
            hops++
        }

        CatalogCache.set(cacheKey, chain, CatalogCache.Bucket.MAPPING)
        return chain
    }

    // ── Top Banners (malId -> bannerImage) ───────────────────────────────────

    suspend fun getTopBanners(limit: Int = 100): Map<Int, String> {
        val cacheKey = "anilist:top-banners:$limit"
        CatalogCache.get<Map<Int, String>>(cacheKey)?.let { return it }

        val query = """
            query (${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) {
                  idMal
                  bannerImage
                }
              }
            }
        """.trimIndent()

        val map = LinkedHashMap<Int, String>()
        val pages = ((limit + 49) / 50).coerceAtLeast(1)
        for (page in 1..pages) {
            val vars = JSONObject().apply { put("page", page) }
            val pageObj = executeGraphQl(query, vars)?.optJSONObject("Page") ?: break
            val arr = pageObj.optJSONArray("media") ?: break
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val idMal = if (m.isNull("idMal")) null else m.optInt("idMal").takeIf { it > 0 }
                val banner = m.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" }
                if (idMal != null && banner != null) {
                    map[idMal] = banner
                }
            }
            if (!pageObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false)!!) break
        }
        if (map.isNotEmpty()) {
            CatalogCache.set(cacheKey, map, CatalogCache.Bucket.MAPPING)
        }
        return map
    }

    // ── Streaming Episodes (last-resort episode title + thumbnail fallback) ──

    suspend fun getStreamingEpisodes(malId: Int): List<AniListStreamingEpisode> {
        val cacheKey = "anilist:episodes:$malId"
        CatalogCache.get<List<AniListStreamingEpisode>>(cacheKey)?.let { return it }

        val query = """
            query (${'$'}malId: Int) {
              Media(idMal: ${'$'}malId, type: ANIME) {
                streamingEpisodes { title thumbnail url site }
              }
            }
        """.trimIndent()
        val vars = JSONObject().apply { put("malId", malId) }
        val arr = executeGraphQl(query, vars)
            ?.optJSONObject("Media")
            ?.optJSONArray("streamingEpisodes")
            ?: return emptyList()

        val list = mutableListOf<AniListStreamingEpisode>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                AniListStreamingEpisode(
                    title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
                    url = obj.optString("url").takeIf { it.isNotBlank() && it != "null" },
                    site = obj.optString("site").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
        CatalogCache.set(cacheKey, list, CatalogCache.Bucket.EPISODES)
        return list
    }

    fun matchAnilistEpisode(episodes: List<AniListStreamingEpisode>, epNum: Int): AniListStreamingEpisode? {
        val epRegex = Regex("(?:episode|ep\\.?)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        return episodes.firstOrNull { ep ->
            val m = epRegex.find(ep.title.orEmpty())
            m?.groupValues?.getOrNull(1)?.toIntOrNull() == epNum
        }
    }

    // ── Anime Images (poster + banner art fallback) ──────────────────────────

    suspend fun getAnimeImages(malId: Int): AniListAnimeImages? {
        val cacheKey = "anilist:images:$malId"
        when (val cached = CatalogCache.lookup<AniListAnimeImages>(cacheKey)) {
            is CatalogCache.CacheLookup.Hit -> return cached.value
            CatalogCache.CacheLookup.Miss -> Unit
        }

        val query = """
            query (${'$'}malId: Int) {
              Media(idMal: ${'$'}malId, type: ANIME) {
                idMal
                title { romaji english }
                coverImage { large extraLarge }
                bannerImage
              }
            }
        """.trimIndent()
        val vars = JSONObject().apply { put("malId", malId) }
        val media = executeGraphQl(query, vars)?.optJSONObject("Media")
        if (media == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
            return null
        }

        val coverObj = media.optJSONObject("coverImage")
        val titleObj = media.optJSONObject("title")
        val poster = coverObj?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
            ?: coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() && it != "null" }
        val posterOriginal = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() && it != "null" } ?: poster
        val cover = media.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" }

        if (poster == null && cover == null) {
            CatalogCache.set(cacheKey, null, CatalogCache.Bucket.MAPPING)
            return null
        }

        val result = AniListAnimeImages(
            idMal = malId,
            title = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                ?: titleObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" },
            poster = poster,
            posterOriginal = posterOriginal,
            cover = cover,
            coverOriginal = cover
        )
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }
}
