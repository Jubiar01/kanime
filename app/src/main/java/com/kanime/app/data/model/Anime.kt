package com.kanime.app.data.model

data class Anime(
    val id: String,
    val title: String,
    val japaneseTitle: String,
    val synopsis: String,
    val score: Double,
    val episodes: Int,
    val duration: String,
    val status: String, // "Airing", "Completed", "Upcoming"
    val season: String,
    val year: Int,
    val genres: List<String>,
    val studio: String,
    val coverUrl: String,
    val bannerUrl: String,
    val episodeList: List<Episode> = emptyList(),
    val isTrending: Boolean = false,
    val isTopAiring: Boolean = false,
    val isSeasonal: Boolean = false,
    // Multi-source catalog IDs & rich metadata (MAL + AniList + TMDB + Kitsu)
    val malId: Int? = null,
    val anilistId: Int? = null,
    val kitsuId: Int? = null,
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val logoUrl: String? = null,
    val type: String? = null,
    val aired: String? = null,
    val rating: String? = null,
    val source: String? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val members: Int? = null,
    val scoredBy: Int? = null,
    val studios: List<String> = emptyList(),
    val streamingPlatforms: List<MalLink> = emptyList(),
    val externalLinks: List<MalLink> = emptyList(),
    val posterSource: String? = null,
    val bannerSource: String? = null,
    val isDetailsLoaded: Boolean = false
)

data class Episode(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val duration: String,
    val thumbnail: String,
    val description: String = "",
    val titleJapanese: String? = null,
    val aired: String? = null,
    val isFiller: Boolean = false,
    val isRecap: Boolean = false,
    val infoSource: String? = null,
    val thumbnailSource: String? = null
)

data class MalLink(
    val name: String,
    val url: String
)

data class MalVoiceActor(
    val peopleId: Int?,
    val name: String,
    val url: String?,
    val image: String?,
    val language: String?
)

data class MalCharacter(
    val characterId: Int?,
    val name: String,
    val url: String?,
    val image: String?,
    val role: String?, // "Main" | "Supporting"
    val voiceActors: List<MalVoiceActor> = emptyList()
)

data class MalCharacterAnime(
    val animeId: Int?,
    val title: String,
    val url: String?,
    val image: String?,
    val role: String?
)

data class MalCharacterDetails(
    val characterId: Int,
    val name: String,
    val nameKanji: String?,
    val nicknames: List<String> = emptyList(),
    val about: String?,
    val note: String?,
    val spoilers: List<String> = emptyList(),
    val favorites: Int?,
    val image: String?,
    val animeography: List<MalCharacterAnime> = emptyList(),
    val voiceActors: List<MalVoiceActor> = emptyList()
)

data class MalPicture(
    val image: String,
    val thumbnail: String?
)

data class MalTheme(
    val number: Int,
    val title: String,
    val artist: String,
    val episodes: String?,
    val spotifyUrl: String?
)

data class MalThemes(
    val opening: List<MalTheme> = emptyList(),
    val ending: List<MalTheme> = emptyList()
)

data class MalVideo(
    val label: String,
    val youtubeId: String?,
    val embedUrl: String,
    val songTitle: String? = null,
    val songArtist: String? = null
)

data class MalVideos(
    val musicVideos: List<MalVideo> = emptyList(),
    val trailers: List<MalVideo> = emptyList()
)

data class MalRecommendation(
    val animeId: Int,
    val title: String,
    val image: String?,
    val votes: Int
)

data class AnimeExtras(
    val characters: List<MalCharacter> = emptyList(),
    val themes: MalThemes = MalThemes(),
    val videos: MalVideos = MalVideos(),
    val pictures: List<MalPicture> = emptyList(),
    val recommendations: List<MalRecommendation> = emptyList(),
    val externalLinks: List<MalLink> = emptyList(),
    val streamingPlatforms: List<MalLink> = emptyList(),
    val charactersLoaded: Boolean = false,
    val mediaLoaded: Boolean = false,
    val moreLoaded: Boolean = false
)
