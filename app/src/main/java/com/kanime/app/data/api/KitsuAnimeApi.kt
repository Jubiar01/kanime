package com.kanime.app.data.api

import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object KitsuAnimeApi {

    private const val BASE_URL = "https://kitsu.io/api/edge"

    suspend fun searchAnime(query: String, limit: Int = 20): List<Anime> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val endpoint = "$BASE_URL/anime?filter[text]=$encodedQuery&page[limit]=$limit"
        fetchAnimeList(endpoint)
    }

    suspend fun getTrending(limit: Int = 15): List<Anime> = withContext(Dispatchers.IO) {
        val endpoint = "$BASE_URL/trending/anime?limit=$limit"
        fetchAnimeList(endpoint)
    }

    suspend fun getTopAiring(limit: Int = 15): List<Anime> = withContext(Dispatchers.IO) {
        val endpoint = "$BASE_URL/anime?filter[status]=current&sort=-userCount&page[limit]=$limit"
        fetchAnimeList(endpoint)
    }

    suspend fun getByCategory(category: String, limit: Int = 15): List<Anime> = withContext(Dispatchers.IO) {
        val encodedCat = URLEncoder.encode(category.lowercase(), "UTF-8")
        val endpoint = "$BASE_URL/anime?filter[categories]=$encodedCat&sort=-userCount&page[limit]=$limit"
        fetchAnimeList(endpoint)
    }

    private fun fetchAnimeList(urlString: String): List<Anime> {
        val result = mutableListOf<Anime>()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.api+json")
                setRequestProperty("User-Agent", "KAnime/1.0 (Android)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                val root = JSONObject(response)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val anime = parseKitsuItem(item)
                        if (anime != null) {
                            result.add(anime)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
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
            val coverUrl = posterObj?.optString("medium")
                ?: posterObj?.optString("large")
                ?: posterObj?.optString("original")
                ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=60"

            val coverObj = attrs.optJSONObject("coverImage")
            val bannerUrl = coverObj?.optString("large")
                ?: coverObj?.optString("original")
                ?: posterObj?.optString("large")
                ?: coverUrl

            val showType = attrs.optString("showType", "TV")
            val ageRating = attrs.optString("ageRating", "PG")
            val genres = listOf(showType, ageRating, "Anime").filter { it.isNotBlank() }

            val epCountForList = episodes.coerceIn(1, 36)
            val episodeList = (1..epCountForList).map { epNum ->
                Episode(
                    id = "${id}_ep_$epNum",
                    episodeNumber = epNum,
                    title = "$title - Episode $epNum",
                    duration = duration,
                    thumbnail = bannerUrl,
                    description = "Episode $epNum of $title with official streaming quality and multi-language subtitles."
                )
            }

            Anime(
                id = id,
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
                isSeasonal = true
            )
        } catch (e: Exception) {
            null
        }
    }
}
