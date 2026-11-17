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
    val isSeasonal: Boolean = false
)

data class Episode(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val duration: String,
    val thumbnail: String,
    val description: String = ""
)
