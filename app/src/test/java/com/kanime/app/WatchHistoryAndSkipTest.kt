package com.kanime.app

import com.kanime.app.data.api.AniSkipApi
import com.kanime.app.data.api.WatchHistoryEntry
import com.kanime.app.data.api.WatchHistoryStore
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.ui.viewmodel.AnimeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryAndSkipTest {

    @Test
    fun defaultWatchlistStartsEmptyWithNoAutoFavorites() {
        val state = AnimeUiState()
        assertTrue(state.watchlistIds.isEmpty())
        assertFalse(state.watchlistIds.contains("fairy_tail_2018"))
        assertFalse(state.watchlistIds.contains("solo_leveling"))
    }

    @Test
    fun parsesAniSkipIntroRecapAndOutroMatchedToStreamLength() {
        val sampleJson = """
            {
              "found": true,
              "results": [
                {
                  "interval": { "startTime": 28.0, "endTime": 118.0 },
                  "skipType": "op",
                  "episodeLength": 1500.0
                },
                {
                  "interval": { "startTime": 90.0, "endTime": 180.0 },
                  "skipType": "mixed-op",
                  "episodeLength": 1440.0
                },
                {
                  "interval": { "startTime": 5.0, "endTime": 88.0 },
                  "skipType": "recap",
                  "episodeLength": 1441.0
                },
                {
                  "interval": { "startTime": 1325.0, "endTime": 1414.0 },
                  "skipType": "ed",
                  "episodeLength": 1440.0
                }
              ]
            }
        """.trimIndent()

        // For a 1440s stream, must reject the 1500s broadcast OP and select the 1440s cut!
        val skipTimes = AniSkipApi.parseResponse(sampleJson, actualEpisodeLengthSec = 1440.0)
        assertNotNull(skipTimes.recap)
        assertNotNull(skipTimes.intro)
        assertNotNull(skipTimes.outro)
        assertEquals(5_000L, skipTimes.recap!!.startMs)
        assertEquals(88_000L, skipTimes.recap!!.endMs)
        assertEquals(90_000L, skipTimes.intro!!.startMs)
        assertEquals(180_000L, skipTimes.intro!!.endMs)

        // Lead-in (1.5s early) right as recap starts -> Skip Recap active
        assertEquals("Skip Recap", skipTimes.activeSegmentAt(3_600L)?.label)
        assertEquals("Skip Recap", skipTimes.activeSegmentAt(50_000L)?.label)
        // Right when intro starts (with 1.5s lead-in) -> Skip Intro active
        assertEquals("Skip Intro", skipTimes.activeSegmentAt(89_000L)?.label)
        assertEquals("Skip Intro", skipTimes.activeSegmentAt(90_000L)?.label)
        assertEquals("Skip Intro", skipTimes.activeSegmentAt(140_000L)?.label)
        // Within last 1.5s of intro -> hidden cleanly so it doesn't linger at the end
        assertNull(skipTimes.activeSegmentAt(179_000L))
        // During outro -> Skip Outro active
        assertEquals("Skip Outro", skipTimes.activeSegmentAt(1_350_000L)?.label)
    }

    @Test
    fun encodesAndDecodesWatchHistoryAndResumeLogic() {
        val anime = Anime(
            id = "mal_52299",
            title = "Solo Leveling",
            japaneseTitle = "俺だけレベルアップな件",
            synopsis = "Action fantasy",
            score = 8.4,
            episodes = 12,
            duration = "24m",
            status = "Completed",
            season = "Winter",
            year = 2024,
            genres = listOf("Action", "Fantasy"),
            studio = "A-1 Pictures",
            coverUrl = "https://example.com/cover.jpg",
            bannerUrl = "https://example.com/banner.jpg",
            malId = 52299,
            englishTitle = "Solo Leveling",
        )
        val ep2 = Episode(
            id = "ep2",
            episodeNumber = 2,
            title = "If I Had One More Chance",
            duration = "24m",
            thumbnail = "https://example.com/ep2.jpg",
        )

        val entries = listOf(
            WatchHistoryEntry(
                anime = anime,
                episodeNumber = ep2.episodeNumber,
                episodeTitle = ep2.title,
                episodeThumbnail = ep2.thumbnail,
                positionMs = 420_000L,
                durationMs = 1_440_000L,
                watchedAtMs = 1_700_000_000_000L,
            ),
        )

        val encoded = WatchHistoryStore.encodeHistory(entries)
        val decoded = WatchHistoryStore.decodeHistory(encoded)
        assertEquals(1, decoded.size)
        assertEquals("mal_52299", decoded[0].anime.id)
        assertEquals(52299, decoded[0].anime.malId)
        assertEquals(2, decoded[0].episodeNumber)
        assertEquals(420_000L, decoded[0].positionMs)
        assertEquals(1_440_000L, decoded[0].durationMs)
        assertFalse(decoded[0].isNearlyFinished)

        // Verify live in-memory recordProgress & getResumePositionMs
        WatchHistoryStore.clearHistory()
        WatchHistoryStore.recordProgress(anime, ep2, positionMs = 420_000L, durationMs = 1_440_000L)
        assertEquals(420_000L, WatchHistoryStore.getResumePositionMs("mal_52299", 2))

        // Nearly finished (>=95%) resets resume to 0L on replay
        WatchHistoryStore.recordProgress(anime, ep2, positionMs = 1_430_000L, durationMs = 1_440_000L)
        assertEquals(0L, WatchHistoryStore.getResumePositionMs("mal_52299", 2))
        WatchHistoryStore.clearHistory()
    }
}
