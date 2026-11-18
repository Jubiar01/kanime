package com.kanime.app.data.api

import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.AnimeExtras
import com.kanime.app.data.model.Episode
import com.kanime.app.data.model.MalCharacter
import com.kanime.app.data.model.MalCharacterDetails
import com.kanime.app.data.model.MalLink
import com.kanime.app.data.model.MalPicture
import com.kanime.app.data.model.MalRecommendation
import com.kanime.app.data.model.MalThemes
import com.kanime.app.data.model.MalVideos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Unified 4-source Anime Catalog & Metadata Orchestrator (ported from SH0MIK/Anivault-API):
 *
 * - MyAnimeList (direct scrape, replaces Jikan): Anime/character details, episodes, pictures,
 *   theme songs, trailers, recommendations, external/streaming links, search fallback
 * - AniList (GraphQL): ID mapping, season lists, catalog discovery, banner art,
 *   streaming-episode titles/thumbnails (fallback)
 * - TMDB: Poster/cover/logo art, episode stills (primary art source)
 * - Kitsu: Poster/cover art, episode stills (fallback if TMDB has nothing)
 */
object AnimeCatalogService {

    private const val FALLBACK_IMAGE =
        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=60"

    data class CatalogSearchResult(
        val results: List<Anime>,
        val source: String // "AniList", "MyAnimeList", "Kitsu", "Offline"
    )

    data class ResolvedAnimeArt(
        val poster: String?,
        val posterSource: String?,
        val cover: String?,
        val coverSource: String?,
        val logo: String?,
        val logoSource: String?
    )

    // ── Home & Explore Catalog Discovery (AniList primary -> Kitsu fallback) ─

    suspend fun getTrending(limit: Int = 20): List<Anime> = withContext(Dispatchers.IO) {
        val aniList = AniListApi.getTrending(perPage = limit)
        if (aniList.isNotEmpty()) {
            return@withContext aniList.map { it.toAnime(isTrending = true) }
        }
        KitsuAnimeApi.getTrending(limit)
    }

    suspend fun getTopAiring(limit: Int = 20): List<Anime> = withContext(Dispatchers.IO) {
        val aniList = AniListApi.getTopAiring(perPage = limit)
        if (aniList.isNotEmpty()) {
            return@withContext aniList.map { it.toAnime(isTopAiring = true) }
        }
        KitsuAnimeApi.getTopAiring(limit)
    }

    suspend fun getSeasonal(limit: Int = 30): List<Anime> = withContext(Dispatchers.IO) {
        val seasonRes = AniListApi.getSeasonNow(perPage = limit)
        if (seasonRes.media.isNotEmpty()) {
            return@withContext seasonRes.media.map { it.toAnime(isSeasonal = true) }
        }
        KitsuAnimeApi.getTopAiring(limit)
    }

    suspend fun browseCatalog(
        genre: String = "All",
        status: String = "All",
        limit: Int = 30
    ): List<Anime> = withContext(Dispatchers.IO) {
        val aniList = AniListApi.browseCatalog(genre = genre, status = status, perPage = limit)
        if (aniList.isNotEmpty()) {
            return@withContext aniList.map { it.toAnime() }
        }
        if (!genre.equals("All", ignoreCase = true)) {
            KitsuAnimeApi.getByCategory(genre, limit)
        } else {
            KitsuAnimeApi.getTrending(limit)
        }
    }

    /**
     * Multi-source search matching Anivault-API's `/api/search`:
     * 1. AniList GraphQL (`SEARCH_MATCH`)
     * 2. MyAnimeList direct scrape fallback (`/anime.php?q=...&cat=anime`)
     * 3. Kitsu fallback
     */
    suspend fun searchAnime(query: String, limit: Int = 20): CatalogSearchResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext CatalogSearchResult(emptyList(), "AniList")

        // 1) AniList GraphQL primary
        val aniResults = AniListApi.searchAnime(q, perPage = limit)
        if (aniResults.isNotEmpty()) {
            return@withContext CatalogSearchResult(
                results = aniResults.map { it.toAnime() },
                source = "AniList"
            )
        }

        // 2) MyAnimeList direct scrape fallback
        val malResults = MalScraperApi.searchAnime(q, limit = limit)
        if (malResults.isNotEmpty()) {
            val topBanners = runCatching { AniListApi.getTopBanners(100) }.getOrDefault(emptyMap())
            val mapped = malResults.map { m ->
                val poster = m.image ?: FALLBACK_IMAGE
                val banner = topBanners[m.malId] ?: poster
                val epCount = (m.episodes ?: 12).coerceAtLeast(1)
                Anime(
                    id = "mal_${m.malId}",
                    title = m.title,
                    japaneseTitle = "",
                    synopsis = "Tap to load full MyAnimeList details...",
                    score = m.score ?: 0.0,
                    episodes = m.episodes ?: 0,
                    duration = "24m",
                    status = "Completed",
                    season = m.type ?: "TV",
                    year = 0,
                    genres = listOfNotNull(m.type, "Anime"),
                    studio = "MyAnimeList",
                    coverUrl = poster,
                    bannerUrl = banner,
                    episodeList = buildPlaceholderEpisodes("mal_${m.malId}", m.title, epCount.coerceAtMost(24), "24m", banner),
                    malId = m.malId,
                    romajiTitle = m.title,
                    type = m.type,
                    posterSource = "mal",
                    bannerSource = if (topBanners.containsKey(m.malId)) "anilist" else "mal"
                )
            }
            return@withContext CatalogSearchResult(mapped, "MyAnimeList")
        }

        // 3) Kitsu fallback
        val kitsuResults = KitsuAnimeApi.searchAnime(q, limit = limit)
        if (kitsuResults.isNotEmpty()) {
            return@withContext CatalogSearchResult(kitsuResults, "Kitsu")
        }

        CatalogSearchResult(emptyList(), "Offline")
    }

    // ── Combined Single Anime Details + Art Fallback Chain (TMDB -> Kitsu -> AniList -> MAL)

    /**
     * Resolves an anime's MAL ID if missing, scrapes full MAL details, and resolves
     * Poster / Cover(Banner) / Logo art via TMDB -> Kitsu -> AniList -> MAL.
     */
    suspend fun enrichAnimeDetails(anime: Anime): Anime = withContext(Dispatchers.IO) {
        val resolvedMalId = resolveMalId(anime) ?: return@withContext anime.copy(isDetailsLoaded = true)
        val details = MalScraperApi.getAnimeDetails(resolvedMalId)
        val anilistId = anime.anilistId ?: runCatching { AniListApi.malToAnilist(resolvedMalId) }.getOrNull()

        val art = resolveAnimeArt(resolvedMalId, details, anime.title)

        val title = details?.titleEnglish?.takeIf { it.isNotBlank() }
            ?: details?.title?.takeIf { it.isNotBlank() }
            ?: anime.title
        val japaneseTitle = details?.titleJapanese?.takeIf { it.isNotBlank() }
            ?: details?.title?.takeIf { it.isNotBlank() && it != title }
            ?: anime.japaneseTitle
        val synopsis = details?.synopsis?.takeIf { it.isNotBlank() } ?: anime.synopsis
        val score = details?.score ?: anime.score
        val episodes = details?.episodes ?: anime.episodes
        val duration = formatMalDuration(details?.duration) ?: anime.duration
        val status = mapMalStatus(details?.status) ?: anime.status
        val (season, year) = parsePremieredOrAired(details?.premiered, details?.aired, anime.season, anime.year)
        val genres = details?.genres?.takeIf { it.isNotEmpty() } ?: anime.genres
        val studios = details?.studios?.takeIf { it.isNotEmpty() } ?: anime.studios
        val primaryStudio = studios.joinToString(", ").ifBlank { anime.studio }

        val finalPoster = art.poster ?: details?.image ?: anime.coverUrl.ifBlank { FALLBACK_IMAGE }
        val finalBanner = art.cover ?: anime.bannerUrl.takeIf { it.isNotBlank() && it != FALLBACK_IMAGE } ?: finalPoster
        val finalPosterSource = art.posterSource ?: if (details?.image != null) "mal" else anime.posterSource
        val finalBannerSource = art.coverSource ?: anime.bannerSource

        anime.copy(
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
            studio = primaryStudio,
            coverUrl = finalPoster,
            bannerUrl = finalBanner,
            malId = resolvedMalId,
            anilistId = anilistId,
            englishTitle = details?.titleEnglish ?: anime.englishTitle,
            romajiTitle = details?.title ?: anime.romajiTitle,
            logoUrl = art.logo ?: anime.logoUrl,
            type = details?.type ?: anime.type,
            aired = details?.aired ?: anime.aired,
            rating = details?.rating ?: anime.rating,
            source = details?.source ?: anime.source,
            rank = details?.rank ?: anime.rank,
            popularity = details?.popularity ?: anime.popularity,
            members = details?.members ?: anime.members,
            scoredBy = details?.scoredBy ?: anime.scoredBy,
            studios = studios,
            streamingPlatforms = details?.streamingPlatforms?.takeIf { it.isNotEmpty() } ?: anime.streamingPlatforms,
            posterSource = finalPosterSource,
            bannerSource = finalBannerSource,
            isDetailsLoaded = true
        )
    }

    suspend fun getAnimeByMalId(
        malId: Int,
        fallbackTitle: String = "Anime #$malId",
        fallbackImage: String? = null
    ): Anime = withContext(Dispatchers.IO) {
        val base = Anime(
            id = "mal_$malId",
            title = fallbackTitle,
            japaneseTitle = "",
            synopsis = "Loading MyAnimeList details...",
            score = 0.0,
            episodes = 0,
            duration = "24m",
            status = "Completed",
            season = "TV",
            year = 0,
            genres = emptyList(),
            studio = "Loading...",
            coverUrl = fallbackImage ?: FALLBACK_IMAGE,
            bannerUrl = fallbackImage ?: FALLBACK_IMAGE,
            malId = malId
        )
        enrichAnimeDetails(base)
    }

    /**
     * Exact Anivault-API art fallback chain: TMDB -> Kitsu -> AniList.
     * Poster and cover(banner) are resolved independently so e.g. a TMDB poster with no backdrop
     * still lets Kitsu or AniList fill in the banner. Logo is TMDB-only.
     */
    suspend fun resolveAnimeArt(
        malId: Int,
        details: MalScraperApi.MalAnimeDetails?,
        fallbackTitle: String? = null
    ): ResolvedAnimeArt = withContext(Dispatchers.IO) {
        val primaryTitle = details?.titleEnglish ?: details?.title ?: fallbackTitle
        var poster: String? = null
        var posterSource: String? = null
        var cover: String? = null
        var coverSource: String? = null
        var logo: String? = null
        var logoSource: String? = null

        // 1) TMDB (primary art source: poster, backdrop/cover, transparent logo)
        if (TmdbApi.isConfigured()) {
            val rawTitles = listOfNotNull(details?.titleEnglish, details?.title, fallbackTitle, details?.titleJapanese)
                .filter { it.isNotBlank() }
                .distinct()
            if (rawTitles.isNotEmpty()) {
                val candidates = TmdbApi.computeTmdbTitleCandidates(rawTitles)
                for (t in candidates.titles) {
                    val tmdbImg = TmdbApi.getAnimeImages(t, candidates.seasonHint)
                    if (tmdbImg != null) {
                        if (tmdbImg.poster != null) { poster = tmdbImg.poster; posterSource = "tmdb" }
                        if (tmdbImg.backdrop != null) { cover = tmdbImg.backdrop; coverSource = "tmdb" }
                        if (tmdbImg.logo != null) { logo = tmdbImg.logo; logoSource = "tmdb" }
                        break
                    }
                }
            }
        }

        // 2) Kitsu (fallback if TMDB missed poster or cover)
        if (poster == null || cover == null) {
            val kitsuId = runCatching { KitsuAnimeApi.getKitsuAnimeId(malId, primaryTitle) }.getOrNull()
            if (kitsuId != null) {
                val kitsuImg = runCatching { KitsuAnimeApi.getAnimeImages(kitsuId) }.getOrNull()
                if (kitsuImg != null) {
                    if (poster == null && kitsuImg.poster != null) {
                        poster = kitsuImg.poster
                        posterSource = "kitsu"
                    }
                    if (cover == null && kitsuImg.cover != null) {
                        cover = kitsuImg.cover
                        coverSource = "kitsu"
                    }
                }
            }
        }

        // 3) AniList (last-resort fallback for poster or banner)
        if (poster == null || cover == null) {
            val alImg = runCatching { AniListApi.getAnimeImages(malId) }.getOrNull()
            if (alImg != null) {
                if (poster == null && alImg.poster != null) {
                    poster = alImg.poster
                    posterSource = "anilist"
                }
                if (cover == null && alImg.cover != null) {
                    cover = alImg.cover
                    coverSource = "anilist"
                }
            }
        }

        ResolvedAnimeArt(poster, posterSource, cover, coverSource, logo, logoSource)
    }

    // ── Combined Episode Metadata + Stills (TMDB -> MAL -> Kitsu -> AniList) ─

    /**
     * Loads the show's episode list from MyAnimeList direct scrape (plus airing-show TMDB tail padding),
     * then enriches episode titles & stills via TMDB -> Kitsu -> AniList streamingEpisodes.
     */
    suspend fun getResolvedEpisodes(anime: Anime): List<Episode> = withContext(Dispatchers.IO) {
        val malId = resolveMalId(anime) ?: return@withContext anime.episodeList
        val details = MalScraperApi.getAnimeDetails(malId)
        val primaryTitle = details?.titleEnglish ?: details?.title ?: anime.title
        val fallbackThumb = anime.bannerUrl.ifBlank { anime.coverUrl }

        // 1) Scrape MAL episodes (first page = up to 100 episodes; fetch additional pages if needed, capped reasonably)
        val malEpisodes = MalScraperApi.getAllEpisodes(malId, maxPages = 12).toMutableList()

        // 2) Airing-show tail padding from TMDB (matches Anivault-API /api/episode)
        val rawTitles = listOfNotNull(details?.titleEnglish, details?.title, anime.title, details?.titleJapanese)
            .filter { it.isNotBlank() }
            .distinct()
        val tmdbCandidates = TmdbApi.computeTmdbTitleCandidates(rawTitles)

        val lastMalEp = malEpisodes.lastOrNull()?.malId ?: 0
        val malStatedTotal = details?.episodes
        val isAiring = details?.status.equals("Currently Airing", ignoreCase = true) || anime.status == "Airing"
        val malScrapeLooksIncomplete = isAiring || (malStatedTotal != null && malStatedTotal > lastMalEp)

        val paddedNumbers = mutableListOf<Int>()
        if (malScrapeLooksIncomplete && TmdbApi.isConfigured()) {
            var tmdbCount: Int? = null
            for (t in tmdbCandidates.titles) {
                val count = TmdbApi.getShowEpisodeCount(t)
                if (count != null) {
                    tmdbCount = count
                    break
                }
            }
            val targetTotal = when {
                malStatedTotal != null -> if (tmdbCount != null) minOf(malStatedTotal, tmdbCount) else malStatedTotal
                else -> tmdbCount ?: lastMalEp
            }
            if (targetTotal > lastMalEp && targetTotal - lastMalEp <= 50) {
                for (num in (lastMalEp + 1)..targetTotal) {
                    paddedNumbers.add(num)
                }
            }
        }

        // Fetch batch art/metadata sources up front to minimize mobile latency:
        // - TMDB season batch (1 call for the hinted/first season when configured)
        // - Kitsu episodes batch (up to 60 eps in 1-3 calls)
        // - AniList streamingEpisodes (1 GraphQL call)
        val (tmdbSeasonMap, kitsuBatchMap, anilistEpisodes) = coroutineScope {
            val tmdbDef = async {
                if (!TmdbApi.isConfigured()) emptyMap()
                else {
                    val seasonToFetch = tmdbCandidates.seasonHint ?: 1
                    var found: Map<Int, TmdbApi.TmdbEpisodeData> = emptyMap()
                    for (t in tmdbCandidates.titles) {
                        val map = TmdbApi.getSeasonEpisodesMap(t, seasonToFetch)
                        if (map.isNotEmpty()) {
                            found = map
                            break
                        }
                    }
                    found
                }
            }
            val kitsuDef = async {
                val kitsuId = runCatching { KitsuAnimeApi.getKitsuAnimeId(malId, primaryTitle) }.getOrNull()
                if (kitsuId != null) {
                    runCatching { KitsuAnimeApi.getEpisodesBatch(kitsuId, maxEpisodes = 60) }.getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            }
            val anilistDef = async {
                runCatching { AniListApi.getStreamingEpisodes(malId) }.getOrDefault(emptyList())
            }
            Triple(tmdbDef.await(), kitsuDef.await(), anilistDef.await())
        }

        // If MAL had zero episode rows (e.g. brand-new airing anime or movie), build episode list from Kitsu / AniList / TMDB / count
        if (malEpisodes.isEmpty()) {
            val total = listOf(
                tmdbSeasonMap.keys.maxOrNull() ?: 0,
                kitsuBatchMap.keys.maxOrNull() ?: 0,
                anilistEpisodes.size,
                details?.episodes ?: anime.episodes
            ).max().coerceIn(1, 50)

            return@withContext (1..total).map { epNum ->
                val tmdbEp = tmdbSeasonMap[epNum]
                val kitsuEp = kitsuBatchMap[epNum]
                val alEp = AniListApi.matchAnilistEpisode(anilistEpisodes, epNum)

                val epTitle = tmdbEp?.title
                    ?: kitsuEp?.title
                    ?: alEp?.title?.replace(Regex("^episode\\s*\\d+\\s*[-:]\\s*", RegexOption.IGNORE_CASE), "")?.trim()
                    ?: "${anime.title} - Episode $epNum"
                val epThumb = tmdbEp?.thumbnail ?: kitsuEp?.thumbnail ?: alEp?.thumbnail ?: fallbackThumb
                val thumbSrc = when {
                    tmdbEp?.thumbnail != null -> "tmdb"
                    kitsuEp?.thumbnail != null -> "kitsu"
                    alEp?.thumbnail != null -> "anilist"
                    else -> null
                }
                val infoSrc = when {
                    tmdbEp?.title != null -> "tmdb"
                    kitsuEp?.title != null -> "kitsu"
                    alEp?.title != null -> "anilist"
                    else -> null
                }

                Episode(
                    id = "${anime.id}_ep_$epNum",
                    episodeNumber = epNum,
                    title = epTitle,
                    duration = anime.duration,
                    thumbnail = epThumb,
                    titleJapanese = kitsuEp?.titleJapanese,
                    aired = tmdbEp?.aired ?: kitsuEp?.aired,
                    infoSource = infoSrc,
                    thumbnailSource = thumbSrc
                )
            }
        }

        // Combine MAL rows + padded rows with TMDB -> MAL -> Kitsu -> AniList resolution
        val allNums: List<Pair<Int, MalScraperApi.MalEpisode?>> =
            malEpisodes.map { it.malId to it } + paddedNumbers.map { it to null }

        // Bounded concurrency (5) for any per-episode TMDB lookups needed beyond the first season batch
        val semaphore = Semaphore(5)
        val resolved = coroutineScope {
            allNums.map { (epNum, malEp) ->
                async {
                    var title: String? = null
                    var titleJapanese: String? = malEp?.titleJapanese
                    var aired: String? = malEp?.aired
                    var infoSource: String? = null
                    var thumbnail: String? = null
                    var thumbnailSource: String? = null

                    // 1) TMDB (use batch map first; for episodes beyond batch, do bounded single lookup up to first 40 eps)
                    val tmdbEp = tmdbSeasonMap[epNum] ?: if (TmdbApi.isConfigured() && epNum <= 40 && tmdbSeasonMap.isEmpty()) {
                        semaphore.withPermit {
                            var hit: TmdbApi.TmdbEpisodeData? = null
                            for (t in tmdbCandidates.titles) {
                                hit = TmdbApi.getEpisodeData(t, epNum, tmdbCandidates.seasonHint, malEp?.aired)
                                if (hit != null) break
                            }
                            hit
                        }
                    } else null

                    if (tmdbEp != null) {
                        if (!tmdbEp.title.isNullOrBlank()) {
                            title = tmdbEp.title
                            aired = tmdbEp.aired ?: aired
                            infoSource = "tmdb"
                        }
                        if (!tmdbEp.thumbnail.isNullOrBlank()) {
                            thumbnail = tmdbEp.thumbnail
                            thumbnailSource = "tmdb"
                        }
                    }

                    // 2) MAL (title / aired / filler / recap)
                    if (title == null && malEp != null && malEp.title.isNotBlank()) {
                        title = malEp.title
                        titleJapanese = malEp.titleJapanese
                        aired = malEp.aired
                        infoSource = "mal"
                    }

                    // 3) Kitsu (fallback for thumbnail & missing title)
                    if (thumbnail == null || title == null) {
                        val kitsuEp = kitsuBatchMap[epNum]
                        if (kitsuEp != null) {
                            if (thumbnail == null && !kitsuEp.thumbnail.isNullOrBlank()) {
                                thumbnail = kitsuEp.thumbnail
                                thumbnailSource = "kitsu"
                            }
                            if (title == null && !kitsuEp.title.isNullOrBlank()) {
                                title = kitsuEp.title
                                titleJapanese = titleJapanese ?: kitsuEp.titleJapanese
                                aired = aired ?: kitsuEp.aired
                                infoSource = "kitsu"
                            }
                        }
                    }

                    // 4) AniList streamingEpisodes (last resort for both)
                    if (thumbnail == null || title == null) {
                        val alMatch = AniListApi.matchAnilistEpisode(anilistEpisodes, epNum)
                        if (alMatch != null) {
                            if (title == null && !alMatch.title.isNullOrBlank()) {
                                title = alMatch.title
                                    .replace(Regex("^episode\\s*\\d+\\s*[-:]\\s*", RegexOption.IGNORE_CASE), "")
                                    .trim()
                                    .ifBlank { alMatch.title }
                                infoSource = "anilist"
                            }
                            if (thumbnail == null && !alMatch.thumbnail.isNullOrBlank()) {
                                thumbnail = alMatch.thumbnail
                                thumbnailSource = "anilist"
                            }
                        }
                    }

                    // Skip empty padded rows that resolved to nothing
                    if (malEp == null && title == null) return@async null

                    Episode(
                        id = "${anime.id}_ep_$epNum",
                        episodeNumber = epNum,
                        title = title ?: "${anime.title} - Episode $epNum",
                        duration = anime.duration,
                        thumbnail = thumbnail ?: fallbackThumb,
                        description = if (aired != null) "Aired: $aired" else "",
                        titleJapanese = titleJapanese,
                        aired = aired,
                        isFiller = malEp?.filler ?: false,
                        isRecap = malEp?.recap ?: false,
                        infoSource = infoSource,
                        thumbnailSource = thumbnailSource
                    )
                }
            }.awaitAll().filterNotNull()
        }

        resolved
    }

    // ── Lazy MAL Extras (Characters, Themes, Videos, Pictures, Recommendations, Links)

    suspend fun getCharacters(malId: Int): List<MalCharacter> = withContext(Dispatchers.IO) {
        MalScraperApi.getCharacters(malId)
    }

    suspend fun getCharacterDetails(characterId: Int): Pair<MalCharacterDetails?, List<MalPicture>> =
        withContext(Dispatchers.IO) {
            val details = MalScraperApi.getCharacterDetails(characterId)
            val pics = MalScraperApi.getCharacterPictures(characterId)
            details to pics
        }

    suspend fun loadMediaExtras(malId: Int): Triple<MalThemes, MalVideos, List<MalPicture>> =
        withContext(Dispatchers.IO) {
            val themes = MalScraperApi.getAnimeThemes(malId)
            val videos = MalScraperApi.getAnimeVideos(malId)
            val pictures = MalScraperApi.getAnimePictures(malId)
            Triple(themes, videos, pictures)
        }

    suspend fun loadMoreExtras(malId: Int): Triple<List<MalRecommendation>, List<MalLink>, List<MalLink>> =
        withContext(Dispatchers.IO) {
            val recs = MalScraperApi.getRecommendations(malId)
            val external = MalScraperApi.getExternalLinks(malId)
            val streaming = MalScraperApi.getStreamingPlatforms(malId)
            Triple(recs, external, streaming)
        }

    // ── ID Resolution Helpers ────────────────────────────────────────────────

    suspend fun resolveMalId(anime: Anime): Int? {
        if (anime.malId != null && anime.malId > 0) return anime.malId
        val cacheKey = "resolve:malId:${anime.id}"
        CatalogCache.get<Int>(cacheKey)?.let { return it }

        // Try AniList search first (fast GraphQL)
        val searchQueries = listOfNotNull(anime.englishTitle, anime.title, anime.romajiTitle)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        for (q in searchQueries) {
            val aniMatches = AniListApi.searchAnime(q, perPage = 5)
            val best = aniMatches.firstOrNull { it.idMal != null }
            if (best?.idMal != null) {
                CatalogCache.set(cacheKey, best.idMal, CatalogCache.Bucket.MAPPING)
                return best.idMal
            }
        }

        // Fallback to direct MAL search
        for (q in searchQueries) {
            val malMatches = MalScraperApi.searchAnime(q, limit = 3)
            val first = malMatches.firstOrNull()
            if (first != null) {
                CatalogCache.set(cacheKey, first.malId, CatalogCache.Bucket.MAPPING)
                return first.malId
            }
        }
        return null
    }

    // ── Conversion Helpers ───────────────────────────────────────────────────

    private fun AniListApi.AniListMedia.toAnime(
        isTrending: Boolean = false,
        isTopAiring: Boolean = false,
        isSeasonal: Boolean = false
    ): Anime {
        val displayTitle = titleEnglish ?: titleRomaji ?: "Anime #$id"
        val jpTitle = titleNative ?: titleRomaji ?: ""
        val poster = coverExtraLarge ?: coverLarge ?: FALLBACK_IMAGE
        val banner = bannerImage ?: poster
        val epCount = episodes ?: 0
        val durStr = "${duration ?: 24}m"
        val score10 = if (averageScore != null) Math.round((averageScore / 10.0) * 10.0) / 10.0 else 0.0
        val mappedStatus = when (status) {
            "RELEASING" -> "Airing"
            "NOT_YET_RELEASED" -> "Upcoming"
            else -> "Completed"
        }
        val mappedSeason = season?.lowercase()?.replaceFirstChar { it.titlecase(Locale.US) }
            ?: format ?: "TV"
        val stableId = if (idMal != null) "mal_$idMal" else "al_$id"

        return Anime(
            id = stableId,
            title = displayTitle,
            japaneseTitle = jpTitle,
            synopsis = description ?: "No synopsis provided.",
            score = score10,
            episodes = epCount,
            duration = durStr,
            status = mappedStatus,
            season = mappedSeason,
            year = seasonYear ?: 0,
            genres = genres.ifEmpty { listOfNotNull(format, "Anime") },
            studio = studio ?: "Animation Studio",
            coverUrl = poster,
            bannerUrl = banner,
            episodeList = buildPlaceholderEpisodes(
                animeId = stableId,
                title = displayTitle,
                count = (if (epCount > 0) epCount else 12).coerceAtMost(24),
                duration = durStr,
                thumbnail = banner
            ),
            isTrending = isTrending,
            isTopAiring = isTopAiring || mappedStatus == "Airing",
            isSeasonal = isSeasonal,
            malId = idMal,
            anilistId = id,
            englishTitle = titleEnglish,
            romajiTitle = titleRomaji,
            type = format,
            studios = listOfNotNull(studio),
            posterSource = "anilist",
            bannerSource = if (bannerImage != null) "anilist" else "anilist"
        )
    }

    private fun buildPlaceholderEpisodes(
        animeId: String,
        title: String,
        count: Int,
        duration: String,
        thumbnail: String
    ): List<Episode> {
        return (1..count.coerceAtLeast(1)).map { epNum ->
            Episode(
                id = "${animeId}_ep_$epNum",
                episodeNumber = epNum,
                title = "$title - Episode $epNum",
                duration = duration,
                thumbnail = thumbnail
            )
        }
    }

    private fun mapMalStatus(malStatus: String?): String? {
        if (malStatus.isNullOrBlank()) return null
        return when {
            malStatus.contains("Currently Airing", ignoreCase = true) -> "Airing"
            malStatus.contains("Not yet aired", ignoreCase = true) -> "Upcoming"
            malStatus.contains("Finished", ignoreCase = true) -> "Completed"
            else -> malStatus
        }
    }

    private fun formatMalDuration(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val minMatch = Regex("(\\d+)\\s*min").find(raw)
        val hrMatch = Regex("(\\d+)\\s*hr").find(raw)
        val hrs = hrMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val mins = minMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val totalMins = hrs * 60 + mins
        return if (totalMins > 0) "${totalMins}m" else raw.substringBefore(" per").trim()
    }

    private fun parsePremieredOrAired(
        premiered: String?,
        aired: String?,
        fallbackSeason: String,
        fallbackYear: Int
    ): Pair<String, Int> {
        if (!premiered.isNullOrBlank()) {
            val parts = premiered.trim().split(Regex("\\s+"))
            val s = parts.getOrNull(0)?.replaceFirstChar { it.titlecase(Locale.US) } ?: fallbackSeason
            val y = parts.getOrNull(1)?.toIntOrNull() ?: fallbackYear
            return s to y
        }
        val yearFromAired = aired?.let { Regex("\\b(19\\d{2}|20\\d{2})\\b").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        return fallbackSeason to (yearFromAired ?: fallbackYear)
    }
}
