package com.kanime.app

import com.kanime.app.data.api.AniListApi
import com.kanime.app.data.api.AnimeCatalogService
import com.kanime.app.data.api.KitsuAnimeApi
import com.kanime.app.data.api.MalScraperApi
import com.kanime.app.data.api.TmdbApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIntegrationTest {

    @Test
    fun tmdbSeasonHintAndAbsoluteEpisodeMappingWork() {
        val hint1 = TmdbApi.extractSeasonHint("Saga of Tanya the Evil II")
        assertEquals("Saga of Tanya the Evil", hint1.base)
        assertEquals(2, hint1.season)

        val hint2 = TmdbApi.extractSeasonHint("Attack on Titan Season 3")
        assertEquals("Attack on Titan", hint2.base)
        assertEquals(3, hint2.season)

        val seasons = listOf(
            TmdbApi.TmdbSeasonInfo(1, 32),
            TmdbApi.TmdbSeasonInfo(2, 21),
            TmdbApi.TmdbSeasonInfo(3, 18)
        )
        val mapped = TmdbApi.mapAbsoluteEpisode(seasons, 54)
        assertEquals(2, mapped.size)
        assertEquals(TmdbApi.TmdbEpisodeCandidate(3, 1), mapped[0])
        assertEquals(TmdbApi.TmdbEpisodeCandidate(3, 54), mapped[1])
    }

    @Test
    fun liveCatalogSourcesWorkEndToEnd() = runBlocking {
        // 1. AniList GraphQL: Trending, Seasonal, ID mapping, Search, Streaming Episodes
        val trending = AniListApi.getTrending(perPage = 5)
        assertTrue("AniList trending should not be empty", trending.isNotEmpty())

        val seasonNow = AniListApi.getSeasonNow(perPage = 5)
        assertTrue("AniList season should not be empty", seasonNow.media.isNotEmpty())

        val narutoAniId = AniListApi.malToAnilist(20)
        assertEquals(20, narutoAniId)

        // 2. MyAnimeList Direct Scrape (replaces Jikan)
        val malDetails = MalScraperApi.getAnimeDetails(20)
        assertNotNull("MAL details for Naruto (20) should load", malDetails)
        assertTrue("MAL title should contain Naruto", malDetails!!.title.contains("Naruto", ignoreCase = true))
        assertTrue("MAL synopsis should not be blank", !malDetails.synopsis.isNullOrBlank())
        assertTrue("MAL genres should not be empty", malDetails.genres.isNotEmpty())

        val malEpisodes = MalScraperApi.getEpisodes(20, page = 1)
        assertTrue("MAL episodes should load", malEpisodes.data.isNotEmpty())

        val malChars = MalScraperApi.getCharacters(20)
        assertTrue("MAL characters should load", malChars.isNotEmpty())

        val firstCharId = malChars.first().characterId
        assertNotNull("First character should have an ID", firstCharId)
        val charDetails = MalScraperApi.getCharacterDetails(firstCharId!!)
        assertNotNull("MAL single character details should load", charDetails)

        val malThemes = MalScraperApi.getAnimeThemes(20)
        assertTrue("MAL themes should have openings or endings", malThemes.opening.isNotEmpty() || malThemes.ending.isNotEmpty())

        val malVideos = MalScraperApi.getAnimeVideos(52991) // Frieren has trailers
        assertTrue("MAL videos should load", malVideos.trailers.isNotEmpty() || malVideos.musicVideos.isNotEmpty())

        val malPics = MalScraperApi.getAnimePictures(20)
        assertTrue("MAL anime pictures should load", malPics.isNotEmpty())

        val malRecs = MalScraperApi.getRecommendations(20)
        assertTrue("MAL recommendations should load", malRecs.isNotEmpty())

        val malExternal = MalScraperApi.getExternalLinks(20)
        assertTrue("MAL external links should load", malExternal.isNotEmpty())

        val malSearch = MalScraperApi.searchAnime("naruto", limit = 5)
        assertTrue("MAL direct search should return results", malSearch.isNotEmpty())

        // 3. Kitsu fallback (MAL mapping -> anime images -> episode batch)
        val kitsuId = KitsuAnimeApi.getKitsuAnimeId(20, "Naruto")
        assertNotNull("Kitsu mapping for MAL 20 should resolve", kitsuId)
        val kitsuImg = KitsuAnimeApi.getAnimeImages(kitsuId!!)
        assertNotNull("Kitsu images should load", kitsuImg)
        assertNotNull("Kitsu poster should exist", kitsuImg!!.poster)
        val kitsuEps = KitsuAnimeApi.getEpisodesBatch(kitsuId, maxEpisodes = 5)
        assertTrue("Kitsu episode batch should load", kitsuEps.isNotEmpty())

        // 4. Combined Orchestrator (Search + Enrich + Resolved Episodes)
        val searchRes = AnimeCatalogService.searchAnime("Frieren", limit = 5)
        assertTrue("Combined search should return results", searchRes.results.isNotEmpty())

        val frierenEnriched = AnimeCatalogService.enrichAnimeDetails(searchRes.results.first())
        assertTrue("Enriched details should be marked loaded", frierenEnriched.isDetailsLoaded)
        assertNotNull("Enriched anime should have MAL ID", frierenEnriched.malId)

        val resolvedEps = AnimeCatalogService.getResolvedEpisodes(frierenEnriched)
        assertTrue("Resolved episodes should not be empty", resolvedEps.isNotEmpty())
        println("VERIFIED: MAL=${malDetails.title} (eps=${malEpisodes.data.size}, chars=${malChars.size}, themes=${malThemes.opening.size}/${malThemes.ending.size}, recs=${malRecs.size}, pics=${malPics.size}, ext=${malExternal.size}, search=${malSearch.size}), KitsuID=$kitsuId, FrierenResolvedEps=${resolvedEps.size}, Ep1=${resolvedEps.first().title} [thumb=${resolvedEps.first().thumbnailSource}]")
    }
}
