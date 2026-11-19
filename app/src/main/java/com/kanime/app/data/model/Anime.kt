package com.kanime.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
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

@Immutable
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

@Immutable
data class MalLink(
    val name: String,
    val url: String
)

@Immutable
data class MalVoiceActor(
    val peopleId: Int?,
    val name: String,
    val url: String?,
    val image: String?,
    val language: String?
)

@Immutable
data class MalCharacter(
    val characterId: Int?,
    val name: String,
    val url: String?,
    val image: String?,
    val role: String?, // "Main" | "Supporting"
    val voiceActors: List<MalVoiceActor> = emptyList()
)

@Immutable
data class MalCharacterAnime(
    val animeId: Int?,
    val title: String,
    val url: String?,
    val image: String?,
    val role: String?
)

@Immutable
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

@Immutable
data class MalPicture(
    val image: String,
    val thumbnail: String?
)

@Immutable
data class MalTheme(
    val number: Int,
    val title: String,
    val artist: String,
    val episodes: String?,
    val spotifyUrl: String?
)

@Immutable
data class MalThemes(
    val opening: List<MalTheme> = emptyList(),
    val ending: List<MalTheme> = emptyList()
)

@Immutable
data class MalVideo(
    val label: String,
    val youtubeId: String?,
    val embedUrl: String,
    val songTitle: String? = null,
    val songArtist: String? = null
)

@Immutable
data class MalVideos(
    val musicVideos: List<MalVideo> = emptyList(),
    val trailers: List<MalVideo> = emptyList()
)

@Immutable
data class MalRecommendation(
    val animeId: Int,
    val title: String,
    val image: String?,
    val votes: Int
)

/**
 * One entry of a franchise chain (prequel -> this -> sequel ...) shown in the detail screen's Seasons row.
 * [label] is what the card says ("Season 2", "Movie", "OVA"); [isCurrent] marks the anime being viewed.
 */
@Immutable
data class AnimeSeasonEntry(
    val malId: Int,
    val title: String,
    val label: String,
    val coverUrl: String?,
    val episodes: Int?,
    val year: Int?,
    val format: String?,
    val isCurrent: Boolean
)

@Immutable
data class AnimeExtras(
    val characters: List<MalCharacter> = emptyList(),
    val themes: MalThemes = MalThemes(),
    val videos: MalVideos = MalVideos(),
    val pictures: List<MalPicture> = emptyList(),
    val recommendations: List<MalRecommendation> = emptyList(),
    val externalLinks: List<MalLink> = emptyList(),
    val streamingPlatforms: List<MalLink> = emptyList(),
    val seasons: List<AnimeSeasonEntry> = emptyList(),
    val charactersLoaded: Boolean = false,
    val mediaLoaded: Boolean = false,
    val moreLoaded: Boolean = false,
    val seasonsLoaded: Boolean = false
)
