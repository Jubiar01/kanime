package com.kanime.app.data.repository

import com.kanime.app.data.api.AnimeCatalogService
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.data.model.MalCharacter
import com.kanime.app.data.model.MalCharacterDetails
import com.kanime.app.data.model.MalLink
import com.kanime.app.data.model.MalPicture
import com.kanime.app.data.model.MalRecommendation
import com.kanime.app.data.model.MalThemes
import com.kanime.app.data.model.MalVideos

object AnimeRepository {

    private fun generateEpisodes(animeId: String, count: Int, baseTitle: String): List<Episode> {
        return (1..count).map { num ->
            Episode(
                id = "${animeId}_ep_$num",
                episodeNumber = num,
                title = "$baseTitle - Episode $num",
                duration = "24m",
                thumbnail = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&auto=format&fit=crop&q=60",
                description = "Episode $num of $baseTitle with breathtaking animation, deep emotional turns, and intense action sequences."
            )
        }
    }

    val sampleAnimes: List<Anime> = listOf(
        Anime(
            id = "fairy_tail_2018",
            title = "Fairy Tail: Final Season",
            japaneseTitle = "FAIRY TAIL（フェアリーテイル）",
            synopsis = "Although Fairy Tail has been disbanded and its members are scattered across Fiore, Natsu Dragneel hasn't given up on reuniting the guild. Along with Lucy Heartfilia and Happy, he embarks on a grand journey to find their comrades and prepare for the ultimate battle against the Alvarez Empire and Acnologia.",
            score = 8.2,
            episodes = 51,
            duration = "24m",
            status = "Completed",
            season = "Fall",
            year = 2018,
            genres = listOf("Action", "Adventure", "Fantasy", "Magic", "Shounen"),
            studio = "A-1 Pictures & Bridge",
            coverUrl = "https://media.kitsu.app/anime/poster_images/13658/medium.jpg",
            bannerUrl = "https://media.kitsu.app/anime/cover_images/13658/large.jpg",
            episodeList = generateEpisodes("fairy_tail_2018", 25, "Fairy Tail"),
            isTrending = true,
            isTopAiring = false,
            isSeasonal = true,
            malId = 35972
        ),
        Anime(
            id = "solo_leveling",
            title = "Solo Leveling: Arise",
            japaneseTitle = "俺だけレベルアップな件",
            synopsis = "In a world where hunters, humans who possess magical abilities, battle deadly monsters to protect humankind from certain annihilation, Sung Jinwoo is notoriously known as the weakest hunter of all mankind. After being mortally wounded in a double dungeon, a mysterious quest window appears before him.",
            score = 8.7,
            episodes = 25,
            duration = "24m",
            status = "Airing",
            season = "Winter",
            year = 2026,
            genres = listOf("Action", "Fantasy", "Supernatural"),
            studio = "A-1 Pictures",
            coverUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("solo_leveling", 12, "Solo Leveling"),
            isTrending = true,
            isTopAiring = true,
            isSeasonal = true,
            malId = 52299
        ),
        Anime(
            id = "frieren",
            title = "Frieren: Beyond Journey's End",
            japaneseTitle = "葬送のフリーレン",
            synopsis = "The demon king has been defeated, and the victorious hero party returns home before disbanding. The four—mage Frieren, hero Himmel, priest Heiter, and warrior Eisen—reminisce over their decade-long journey. As an elf, Frieren lives for thousands of years and begins a new journey to understand humanity.",
            score = 9.3,
            episodes = 28,
            duration = "24m",
            status = "Completed",
            season = "Fall",
            year = 2025,
            genres = listOf("Adventure", "Drama", "Fantasy"),
            studio = "Madhouse",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("frieren", 28, "Frieren"),
            isTrending = true,
            isTopAiring = false,
            isSeasonal = false,
            malId = 52991
        ),
        Anime(
            id = "jujutsu_kaisen",
            title = "Jujutsu Kaisen",
            japaneseTitle = "呪術廻戦",
            synopsis = "Idly indulging in supernatural activities with the Occult Club, high schooler Yuji Itadori spends his days at either the clubroom or the hospital. However, this leisurely lifestyle takes a turn for the strange when he unknowingly swallows a cursed finger.",
            score = 8.9,
            episodes = 47,
            duration = "24m",
            status = "Completed",
            season = "Fall",
            year = 2024,
            genres = listOf("Action", "Fantasy", "Supernatural", "Shounen"),
            studio = "MAPPA",
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("jujutsu_kaisen", 24, "Jujutsu Kaisen"),
            isTrending = true,
            isTopAiring = false,
            isSeasonal = false,
            malId = 40748
        ),
        Anime(
            id = "dandadan",
            title = "Dandadan",
            japaneseTitle = "ダンダダン",
            synopsis = "Momo Ayase strikes up an unusual friendship with her school's UFO fanatic, whom she nicknames Okarun. While Momo believes in ghosts, she thinks aliens are utter nonsense. Her new friend thinks the exact opposite. To settle the score, both embark on separate supernatural visits.",
            score = 8.6,
            episodes = 12,
            duration = "24m",
            status = "Airing",
            season = "Fall",
            year = 2026,
            genres = listOf("Action", "Comedy", "Sci-Fi", "Supernatural"),
            studio = "Science SARU",
            coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("dandadan", 12, "Dandadan"),
            isTrending = true,
            isTopAiring = true,
            isSeasonal = true,
            malId = 57334
        ),
        Anime(
            id = "demon_slayer",
            title = "Demon Slayer: Infinity Castle",
            japaneseTitle = "鬼滅の刃 無限城編",
            synopsis = "Tanjiro Kamado, joined by Inosuke, Zenitsu, and the Hashira, plunges deep into the shifting labyrinths of the Infinity Castle to confront Muzan Kibutsuji and the remaining Upper Ranks in the ultimate battle of endurance and resolve.",
            score = 9.0,
            episodes = 12,
            duration = "26m",
            status = "Airing",
            season = "Spring",
            year = 2026,
            genres = listOf("Action", "Fantasy", "Historical", "Shounen"),
            studio = "ufotable",
            coverUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("demon_slayer", 12, "Demon Slayer"),
            isTrending = false,
            isTopAiring = true,
            isSeasonal = true,
            malId = 38000
        ),
        Anime(
            id = "chainsaw_man",
            title = "Chainsaw Man: Reze Arc",
            japaneseTitle = "チェンソーマン レゼ篇",
            synopsis = "Denji is a teenage boy living with a Chainsaw Devil named Pochita. Due to the debt his father left behind, he has been living a rock-bottom life while repaying his debt by harvesting devil corpses with Pochita. When he encounters the enigmatic Reze, his heart and life enter turbulent waters.",
            score = 8.8,
            episodes = 12,
            duration = "24m",
            status = "Upcoming",
            season = "Winter",
            year = 2026,
            genres = listOf("Action", "Supernatural", "Drama"),
            studio = "MAPPA",
            coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("chainsaw_man", 12, "Chainsaw Man"),
            isTrending = true,
            isTopAiring = false,
            isSeasonal = true,
            malId = 44511
        ),
        Anime(
            id = "oshi_no_ko",
            title = "Oshi no Ko",
            japaneseTitle = "【推しの子】",
            synopsis = "In the entertainment world, lies are weapons. Gorou Amemiya is an obstetrician practicing in a countryside town with no connection to the glittering world of showbiz. However, an encounter with his favorite idol, Ai Hoshino, sparks a dramatic unraveling of destiny and secrets.",
            score = 8.7,
            episodes = 24,
            duration = "24m",
            status = "Completed",
            season = "Summer",
            year = 2025,
            genres = listOf("Drama", "Mystery", "Supernatural"),
            studio = "Doga Kobo",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("oshi_no_ko", 24, "Oshi no Ko"),
            isTrending = false,
            isTopAiring = false,
            isSeasonal = false,
            malId = 52034
        ),
        Anime(
            id = "bleach_tybw",
            title = "Bleach: Thousand-Year Blood War",
            japaneseTitle = "BLEACH 千年血戦篇",
            synopsis = "The peace is suddenly broken when warning sirens blare through the Soul Society. Residents are disappearing without a trace and nobody knows who's behind it. Meanwhile, a dark shadow is also extending itself toward Ichigo and his friends in Karakura Town.",
            score = 9.1,
            episodes = 39,
            duration = "24m",
            status = "Airing",
            season = "Fall",
            year = 2026,
            genres = listOf("Action", "Adventure", "Fantasy", "Shounen"),
            studio = "Pierrot Films",
            coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=60",
            bannerUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=1200&auto=format&fit=crop&q=80",
            episodeList = generateEpisodes("bleach_tybw", 13, "Bleach TYBW"),
            isTrending = true,
            isTopAiring = true,
            isSeasonal = true,
            malId = 41467
        )
    )

    val genres: List<String> = listOf(
        "All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
        "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
        "Slice of Life", "Sports", "Supernatural", "Thriller"
    )

    suspend fun searchOnlineWithSource(query: String): AnimeCatalogService.CatalogSearchResult {
        val result = AnimeCatalogService.searchAnime(query)
        if (result.results.isNotEmpty()) {
            return result
        }
        val fallback = sampleAnimes.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.japaneseTitle.contains(query, ignoreCase = true)
        }
        return AnimeCatalogService.CatalogSearchResult(fallback, "Offline")
    }

    suspend fun searchOnline(query: String): List<Anime> {
        return searchOnlineWithSource(query).results
    }

    suspend fun getTrending(): List<Anime> {
        val online = AnimeCatalogService.getTrending(20)
        return if (online.isNotEmpty()) online else sampleAnimes.filter { it.isTrending }
    }

    suspend fun getTopAiring(): List<Anime> {
        val online = AnimeCatalogService.getTopAiring(20)
        return if (online.isNotEmpty()) online else sampleAnimes.filter { it.isTopAiring }
    }

    suspend fun getSeasonal(): List<Anime> {
        val online = AnimeCatalogService.getSeasonal(30)
        return if (online.isNotEmpty()) online else sampleAnimes.filter { it.isSeasonal }
    }

    suspend fun browseCatalog(genre: String, status: String): List<Anime> {
        val online = AnimeCatalogService.browseCatalog(genre = genre, status = status, limit = 30)
        if (online.isNotEmpty()) return online
        return sampleAnimes.filter { anime ->
            val matchesGenre = genre == "All" || anime.genres.any { it.equals(genre, ignoreCase = true) }
            val matchesStatus = status == "All" || anime.status.equals(status, ignoreCase = true)
            matchesGenre && matchesStatus
        }
    }

    suspend fun enrichAnimeDetails(anime: Anime): Anime =
        AnimeCatalogService.enrichAnimeDetails(anime)

    suspend fun getAnimeByMalId(malId: Int, fallbackTitle: String, fallbackImage: String?): Anime =
        AnimeCatalogService.getAnimeByMalId(malId, fallbackTitle, fallbackImage)

    suspend fun getResolvedEpisodes(anime: Anime): List<Episode> =
        AnimeCatalogService.getResolvedEpisodes(anime)

    suspend fun getCharacters(malId: Int): List<MalCharacter> =
        AnimeCatalogService.getCharacters(malId)

    suspend fun getCharacterDetails(characterId: Int): Pair<MalCharacterDetails?, List<MalPicture>> =
        AnimeCatalogService.getCharacterDetails(characterId)

    suspend fun loadMediaExtras(malId: Int): Triple<MalThemes, MalVideos, List<MalPicture>> =
        AnimeCatalogService.loadMediaExtras(malId)

    suspend fun loadMoreExtras(malId: Int): Triple<List<MalRecommendation>, List<MalLink>, List<MalLink>> =
        AnimeCatalogService.loadMoreExtras(malId)
}
