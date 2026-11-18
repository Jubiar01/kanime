package com.kanime.app.data.api

import com.kanime.app.data.model.MalCharacter
import com.kanime.app.data.model.MalCharacterAnime
import com.kanime.app.data.model.MalCharacterDetails
import com.kanime.app.data.model.MalLink
import com.kanime.app.data.model.MalPicture
import com.kanime.app.data.model.MalRecommendation
import com.kanime.app.data.model.MalTheme
import com.kanime.app.data.model.MalThemes
import com.kanime.app.data.model.MalVideo
import com.kanime.app.data.model.MalVideos
import com.kanime.app.data.model.MalVoiceActor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Direct MyAnimeList.net HTML scraper (replaces Jikan), ported from SH0MIK/Anivault-API (src/scrapers/mal.ts).
 *
 * All live requests go through a serialized rate-limit queue (concurrency 1 with 700ms spacing)
 * so MyAnimeList does not return 429 or temporarily IP-ban the client.
 * Cache hits bypass the queue completely.
 */
object MalScraperApi {

    private const val BASE = "https://myanimelist.net"
    private const val SCRAPE_DELAY_MS = 700L
    private const val EPISODES_PER_PAGE = 100
    private const val RECOMMENDATIONS_LIMIT = 12

    private val malMutex = Mutex()
    @Volatile
    private var lastRequestAtMs = 0L

    private val FOOTER_DOMAINS = listOf(
        "facebook.com", "x.com", "instagram.com", "discord.gg",
        "apps.apple.com", "play.google.com", "policies.google.com",
        "otakumode.com", "honeyfeed.fm"
    )

    data class MalAnimeDetails(
        val malId: Int,
        val title: String,
        val titleEnglish: String?,
        val titleJapanese: String?,
        val synopsis: String?,
        val image: String?,
        val type: String?,
        val episodes: Int?,
        val status: String?,
        val aired: String?,
        val premiered: String?,
        val duration: String?,
        val rating: String?,
        val score: Double?,
        val scoredBy: Int?,
        val rank: Int?,
        val popularity: Int?,
        val members: Int?,
        val genres: List<String>,
        val studios: List<String>,
        val source: String?,
        val streamingPlatforms: List<MalLink>,
        val themes: MalThemes = MalThemes()
    )

    data class MalSearchResult(
        val malId: Int,
        val title: String,
        val image: String?,
        val type: String?,
        val episodes: Int?,
        val score: Double?,
        val url: String
    )

    data class MalEpisode(
        val malId: Int, // episode number
        val url: String,
        val title: String,
        val titleJapanese: String?,
        val aired: String?,
        val filler: Boolean,
        val recap: Boolean
    )

    data class MalEpisodePage(
        val data: List<MalEpisode>,
        val currentPage: Int,
        val hasNextPage: Boolean
    )

    // ── Serialized rate-limited HTTP fetch ───────────────────────────────────

    private suspend fun fetchMalHtml(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        useMobileUa: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        malMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestAtMs
            if (lastRequestAtMs > 0 && elapsed < SCRAPE_DELAY_MS) {
                delay(SCRAPE_DELAY_MS - elapsed)
            }
            try {
                val urlBuilder = "$BASE$path".toHttpUrl().newBuilder()
                queryParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header(
                        "User-Agent",
                        if (useMobileUa) CatalogConfig.MOBILE_USER_AGENT else CatalogConfig.DESKTOP_USER_AGENT
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "$BASE/")
                    .build()

                CatalogConfig.httpClient.newCall(request).execute().use { response ->
                    lastRequestAtMs = System.currentTimeMillis()
                    if (!response.isSuccessful) return@withLock null
                    response.body.string()
                }
            } catch (_: Exception) {
                lastRequestAtMs = System.currentTimeMillis()
                null
            }
        }
    }

    // ── Common HTML & URL helpers ────────────────────────────────────────────

    private fun fullSizeImage(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.replace(Regex("/r/\\d+x\\d+/"), "/")
    }

    private fun idFromUrl(url: String?): Int? {
        if (url.isNullOrBlank()) return null
        val match = Regex("/(\\d+)/").find(url)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun stripTags(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractBlock(
        html: String,
        startLabel: String,
        endLabels: List<String>,
        fromIndex: Int = 0
    ): String {
        val startIdx = html.indexOf(startLabel, fromIndex)
        if (startIdx == -1) return ""
        var endIdx = html.length
        for (label in endLabels) {
            val idx = html.indexOf(label, startIdx + startLabel.length)
            if (idx != -1 && idx < endIdx) endIdx = idx
        }
        return html.substring(startIdx, endIdx)
    }

    private fun sidebarRow(doc: Document, label: String): Element? {
        val target = label.lowercase()
        var match: Element? = null
        for (el in doc.select(".leftside .spaceit_pad, .leftside .spaceit")) {
            val dark = el.selectFirst("span.dark_text") ?: continue
            val rowLabel = dark.text().trim().removeSuffix(":").lowercase()
            if (rowLabel == target) {
                match = el
            }
        }
        return match
    }

    private fun sidebarText(doc: Document, label: String): String? {
        val row = sidebarRow(doc, label) ?: return null
        val clone = row.clone()
        clone.select("span.dark_text").remove()
        val text = clone.text().trim().replace(Regex("^,\\s*"), "")
        return if (text.isNotBlank() && !text.equals("unknown", ignoreCase = true) && !text.equals("add some", ignoreCase = true)) {
            text
        } else {
            null
        }
    }

    private fun sidebarLinks(doc: Document, label: String): List<String> {
        val row = sidebarRow(doc, label) ?: return emptyList()
        return row.select("a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("add some", ignoreCase = true) }
            .distinct()
    }

    // ── 1. Anime Details + Streaming Platforms + Themes (/anime/{id}) ────────

    private fun parseStreamingPlatforms(html: String): List<MalLink> {
        val block = extractBlock(
            html,
            "Streaming Platforms",
            listOf("May be unavailable in your region", "Characters &amp; Staff", "Characters & Staff")
        )
        if (block.isBlank()) return emptyList()

        val doc = Jsoup.parseBodyFragment(block)
        val out = mutableListOf<MalLink>()
        val seen = HashSet<String>()

        for (a in doc.select("a[href^=http]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || href.contains("myanimelist.net") || seen.contains(href)) continue
            val name = a.attr("title").trim().ifBlank { a.text().trim() }
            if (name.isBlank()) continue
            seen.add(href)
            out.add(MalLink(name = name, url = href))
        }
        return out
    }

    private fun parseThemeBlock(text: String): List<MalTheme> {
        if (text.isBlank()) return emptyList()
        val themes = mutableListOf<MalTheme>()
        val chunks = text.split(Regex("(?=\\d+\\s*:\\s*\")"))
        var idx = 0
        for (chunk in chunks) {
            val m = Regex("^(\\d+)\\s*:\\s*\"([^\"]+)\"\\s*by\\s*([\\s\\S]*)$").find(chunk) ?: continue
            idx++
            val num = m.groupValues[1].toIntOrNull() ?: idx
            val title = m.groupValues[2].trim()
            var rest = m.groupValues[3]

            var episodes: String? = null
            val epMatch = Regex("\\(eps?\\.?\\s*([^)]+)\\)", RegexOption.IGNORE_CASE).find(rest)
            if (epMatch != null) {
                episodes = epMatch.groupValues[1].trim()
                rest = rest.substring(0, epMatch.range.first)
            }

            var artist = rest.replace(Regex("\\b(Edit|MV|Preview)\\b[\\s\\S]*$", RegexOption.IGNORE_CASE), "").trim()
            artist = artist.replace(Regex("[,\\s]+$"), "").trim()

            themes.add(MalTheme(number = num, title = title, artist = artist, episodes = episodes, spotifyUrl = null))
        }
        return themes
    }

    private fun extractSpotifyIds(blockHtml: String): List<String?> {
        if (blockHtml.isBlank()) return emptyList()
        val doc = Jsoup.parseBodyFragment(blockHtml)
        val regex = Regex("openMusicLinkPopup\\(\\s*'[^']*'\\s*,\\s*'(?:[^'\\\\]|\\\\.)*'\\s*,\\s*'(?:[^'\\\\]|\\\\.)*'\\s*,\\s*'([^']*)'\\s*\\)")
        return doc.select("[onclick*=openMusicLinkPopup], a[href*=openMusicLinkPopup]").map { el ->
            val raw = el.attr("onclick").ifBlank { el.attr("href") }
            regex.find(raw)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseThemesFromHtml(html: String): MalThemes {
        val openingBlock = extractBlock(html, "Opening Theme", listOf("Ending Theme", "More Videos", "Episode Videos"))
        val endingBlock = extractBlock(html, "Ending Theme", listOf("More Videos", "Episode Videos"))

        val openingIds = extractSpotifyIds(openingBlock)
        val endingIds = extractSpotifyIds(endingBlock)

        val opening = parseThemeBlock(stripTags(openingBlock)).mapIndexed { i, t ->
            val spotifyId = openingIds.getOrNull(i)
            t.copy(spotifyUrl = spotifyId?.let { "https://open.spotify.com/track/$it" })
        }
        val ending = parseThemeBlock(stripTags(endingBlock)).mapIndexed { i, t ->
            val spotifyId = endingIds.getOrNull(i)
            t.copy(spotifyUrl = spotifyId?.let { "https://open.spotify.com/track/$it" })
        }
        return MalThemes(opening = opening, ending = ending)
    }

    suspend fun getAnimeDetails(malId: Int): MalAnimeDetails? {
        val cacheKey = "mal:anime:$malId"
        CatalogCache.get<MalAnimeDetails>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/anime/$malId") ?: return null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.title-name strong")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("span[itemprop=name]")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null

        val synopsisRaw = doc.selectFirst("p[itemprop=description]")?.text()?.trim().orEmpty()
        val synopsis = synopsisRaw
            .replace(Regex("\\[Written by MAL Rewrite\\]", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotBlank() }

        val imgEl = doc.selectFirst("img[itemprop=image]")
        val image = fullSizeImage(imgEl?.attr("data-src")?.ifBlank { null } ?: imgEl?.attr("src"))

        val score = doc.selectFirst("span[itemprop=ratingValue]")?.text()?.trim()?.toDoubleOrNull()
        val scoredBy = doc.selectFirst("span[itemprop=ratingCount]")?.text()?.trim()?.replace(",", "")?.toIntOrNull()

        val episodes = sidebarText(doc, "Episodes")?.toIntOrNull()
        val rank = sidebarText(doc, "Rank")?.replace(Regex("[#,]"), "")?.toIntOrNull()
        val popularity = sidebarText(doc, "Popularity")?.replace(Regex("[#,]"), "")?.toIntOrNull()
        val members = sidebarText(doc, "Members")?.replace(",", "")?.toIntOrNull()

        val genres = (sidebarLinks(doc, "Genres") + sidebarLinks(doc, "Genre") + sidebarLinks(doc, "Themes") + sidebarLinks(doc, "Demographic")).distinct()
        val studios = sidebarLinks(doc, "Studios")
        val themes = parseThemesFromHtml(html)

        val details = MalAnimeDetails(
            malId = malId,
            title = title,
            titleEnglish = sidebarText(doc, "English"),
            titleJapanese = sidebarText(doc, "Japanese"),
            synopsis = synopsis,
            image = image,
            type = sidebarText(doc, "Type"),
            episodes = episodes,
            status = sidebarText(doc, "Status"),
            aired = sidebarText(doc, "Aired"),
            premiered = sidebarText(doc, "Premiered"),
            duration = sidebarText(doc, "Duration"),
            rating = sidebarText(doc, "Rating"),
            score = score,
            scoredBy = scoredBy,
            rank = rank,
            popularity = popularity,
            members = members,
            genres = genres,
            studios = studios,
            source = sidebarText(doc, "Source"),
            streamingPlatforms = parseStreamingPlatforms(html),
            themes = themes
        )

        CatalogCache.set(cacheKey, details, CatalogCache.Bucket.MAPPING)
        CatalogCache.set("mal:themes:$malId", themes, CatalogCache.Bucket.MAPPING)
        return details
    }

    suspend fun getStreamingPlatforms(malId: Int): List<MalLink> {
        return getAnimeDetails(malId)?.streamingPlatforms ?: emptyList()
    }

    suspend fun getAnimeThemes(malId: Int): MalThemes {
        val cacheKey = "mal:themes:$malId"
        CatalogCache.get<MalThemes>(cacheKey)?.let { return it }

        // Reuse getAnimeDetails() since themes sit on the exact same /anime/{id} page
        val details = getAnimeDetails(malId)
        if (details != null) {
            return details.themes
        }
        return MalThemes()
    }

    // ── 2. Text Search (/anime.php?q={q}&cat=anime) ──────────────────────────

    suspend fun searchAnime(query: String, limit: Int = 12): List<MalSearchResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val cacheKey = "mal:search:${q.lowercase()}:$limit"
        CatalogCache.get<List<MalSearchResult>>(cacheKey)?.let { return it }

        val html = fetchMalHtml(
            path = "/anime.php",
            queryParams = mapOf("q" to q, "cat" to "anime"),
            useMobileUa = true
        ) ?: return emptyList()

        val doc = Jsoup.parse(html)
        val parsed = parseMobileSearchRows(doc).ifEmpty { parseDesktopSearchRows(doc) }.take(limit)
        CatalogCache.set(cacheKey, parsed, CatalogCache.Bucket.STREAM)
        return parsed
    }

    private fun parseMobileSearchRows(doc: Document): List<MalSearchResult> {
        val results = mutableListOf<MalSearchResult>()
        val seen = HashSet<Int>()

        for (card in doc.select("a.box-unit1-btn")) {
            val href = card.attr("href").trim()
            val malId = Regex("/anime/(\\d+)/").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            if (!seen.add(malId)) continue

            val title = card.selectFirst("li.title")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val imgEl = card.selectFirst("img")
            val image = fullSizeImage(imgEl?.attr("data-src")?.ifBlank { null } ?: imgEl?.attr("src"))

            val scoreLi = card.select("li").firstOrNull { li ->
                li.selectFirst("span")?.text()?.trim() == "Score"
            }
            val scoreText = scoreLi?.let {
                val clone = it.clone()
                clone.select("span").remove()
                clone.text().trim()
            }
            val score = scoreText?.toDoubleOrNull()

            val ddText = card.selectFirst("dd")?.text()?.trim().orEmpty()
            val ddMatch = Regex("^(\\S+)\\s+(\\d+)\\s*eps?", RegexOption.IGNORE_CASE).find(ddText)

            results.add(
                MalSearchResult(
                    malId = malId,
                    title = title,
                    image = image,
                    type = ddMatch?.groupValues?.getOrNull(1),
                    episodes = ddMatch?.groupValues?.getOrNull(2)?.toIntOrNull(),
                    score = score,
                    url = if (href.startsWith("http")) href else "$BASE$href"
                )
            )
        }
        return results
    }

    private fun parseDesktopSearchRows(doc: Document): List<MalSearchResult> {
        val results = mutableListOf<MalSearchResult>()
        val seen = HashSet<Int>()

        for (tr in doc.select("table tr")) {
            val titleLink = tr.selectFirst("a.hoverinfo_trigger[href*=/anime/] strong")?.parent()
                ?: continue
            val href = titleLink.attr("href").trim()
            val malId = Regex("/anime/(\\d+)/").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            if (!seen.add(malId)) continue

            val title = titleLink.text().trim()
            if (title.isBlank()) continue
            val imgEl = tr.selectFirst("img")
            val image = fullSizeImage(imgEl?.attr("data-src")?.ifBlank { null } ?: imgEl?.attr("src"))
            val tds = tr.select("td")
            val type = tds.getOrNull(2)?.text()?.trim()?.takeIf { it.isNotBlank() }
            val episodes = tds.getOrNull(3)?.text()?.trim()?.toIntOrNull()
            val score = tds.getOrNull(4)?.text()?.trim()?.toDoubleOrNull()

            results.add(
                MalSearchResult(
                    malId = malId,
                    title = title,
                    image = image,
                    type = type,
                    episodes = episodes,
                    score = score,
                    url = if (href.startsWith("http")) href else "$BASE$href"
                )
            )
        }
        return results
    }

    // ── 3. Episodes (/anime/{id}/_/episode?offset=N) ─────────────────────────

    private fun parseEpisodeRows(doc: Document, malId: Int): List<MalEpisode> {
        val episodes = mutableListOf<MalEpisode>()
        val seen = HashSet<Int>()

        for (row in doc.select("tr")) {
            val numCell = row.selectFirst("td.episode-number") ?: continue
            val titleCell = row.selectFirst("td.episode-title") ?: continue

            // Guard against MAL's hidden "Compact" summary row that stacks all anchors in one cell
            val anchors = titleCell.select("a")
            if (anchors.size != 1) continue

            val rawNum = numCell.text().trim()
            if (!Regex("^\\d{1,4}$").matches(rawNum)) continue
            val num = rawNum.toIntOrNull() ?: continue
            if (!seen.add(num)) continue

            val link = anchors.first() ?: continue
            val title = link.text().trim()
            if (title.isBlank()) continue

            val titleJapanese = titleCell.selectFirst("span")?.text()?.trim()?.takeIf { it.isNotBlank() }
            var aired = row.selectFirst("td.episode-aired")?.text()?.trim()?.takeIf { it.isNotBlank() }
            if (aired != null && aired.length > 40) aired = null
            val cellText = titleCell.text().lowercase()

            val href = link.attr("href").trim().takeIf { it.isNotBlank() }
            val url = when {
                href == null -> "$BASE/anime/$malId/_/episode/$num"
                href.startsWith("http") -> href
                else -> "$BASE$href"
            }

            episodes.add(
                MalEpisode(
                    malId = num,
                    url = url,
                    title = title,
                    titleJapanese = titleJapanese,
                    aired = aired,
                    filler = cellText.contains("filler"),
                    recap = cellText.contains("recap")
                )
            )
        }
        return episodes
    }

    suspend fun getEpisodes(malId: Int, page: Int = 1): MalEpisodePage {
        val cacheKey = "mal:episodes:$malId:$page"
        val details = runCatching { getAnimeDetails(malId) }.getOrNull()
        val isAiring = details?.status.equals("Currently Airing", ignoreCase = true)

        if (!isAiring) {
            CatalogCache.get<MalEpisodePage>(cacheKey)?.let { return it }
        }

        val offset = (page - 1) * EPISODES_PER_PAGE
        val params = if (offset > 0) mapOf("offset" to offset.toString()) else emptyMap()
        val html = fetchMalHtml("/anime/$malId/_/episode", params)
            ?: return MalEpisodePage(emptyList(), page, false)

        val doc = Jsoup.parse(html)
        val data = parseEpisodeRows(doc, malId)
        val result = MalEpisodePage(
            data = data,
            currentPage = page,
            hasNextPage = data.size == EPISODES_PER_PAGE
        )
        if (!isAiring) {
            CatalogCache.set(cacheKey, result, CatalogCache.Bucket.EPISODES)
        }
        return result
    }

    suspend fun getAllEpisodes(malId: Int, maxPages: Int = 15): List<MalEpisode> {
        val all = mutableListOf<MalEpisode>()
        var page = 1
        while (page <= maxPages) {
            val pageRes = getEpisodes(malId, page)
            all.addAll(pageRes.data)
            if (!pageRes.hasNextPage) break
            page++
        }
        return all
    }

    suspend fun getEpisode(malId: Int, epNum: Int): MalEpisode? {
        if (epNum < 1) return null
        val page = ((epNum - 1) / EPISODES_PER_PAGE) + 1
        return getEpisodes(malId, page).data.firstOrNull { it.malId == epNum }
    }

    // ── 4. Characters + Voice Actors (/anime/{id}/_/characters) ──────────────

    suspend fun getCharacters(malId: Int): List<MalCharacter> {
        val cacheKey = "mal:characters:$malId"
        CatalogCache.get<List<MalCharacter>>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/anime/$malId/_/characters") ?: return emptyList()
        val doc = Jsoup.parse(html)

        val byId = LinkedHashMap<Int, MalCharacter>()

        for (table in doc.select("table")) {
            val charLink = table.select("a[href*=/character/]").firstOrNull { it.text().trim().isNotEmpty() }
                ?: continue
            val charUrl = charLink.attr("href").trim().takeIf { it.isNotBlank() }
            val id = idFromUrl(charUrl) ?: continue
            val name = charLink.text().trim()
            if (name.isBlank()) continue

            val charImgEl = table.selectFirst("img[src*=/characters/], img[data-src*=/characters/]")
            val charImg = fullSizeImage(charImgEl?.attr("data-src")?.ifBlank { null } ?: charImgEl?.attr("src"))

            val nameCell = charLink.closest("td")
            var role = nameCell?.text()
                ?.replace(name, "")
                ?.replace(Regex("[\\d,]+\\s*Favorites", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (role != null && role.length > 20) role = null

            val voiceActors = mutableListOf<MalVoiceActor>()
            for (a in table.select("a[href*=/people/]")) {
                val vaName = a.text().trim()
                if (vaName.isBlank()) continue
                val vaUrl = a.attr("href").trim().takeIf { it.isNotBlank() }
                val vaCell = a.closest("td")
                val language = vaCell?.text()?.replace(vaName, "")?.trim()?.takeIf { it.isNotBlank() }
                val vaImgEl = vaCell?.selectFirst("img") ?: vaCell?.nextElementSibling()?.selectFirst("img")
                val vaImg = fullSizeImage(vaImgEl?.attr("data-src")?.ifBlank { null } ?: vaImgEl?.attr("src"))
                voiceActors.add(
                    MalVoiceActor(
                        peopleId = idFromUrl(vaUrl),
                        name = vaName,
                        url = vaUrl,
                        image = vaImg,
                        language = language
                    )
                )
            }

            val existing = byId[id]
            if (existing == null) {
                byId[id] = MalCharacter(
                    characterId = id,
                    name = name,
                    url = charUrl,
                    image = charImg,
                    role = role,
                    voiceActors = voiceActors
                )
            } else {
                val seenVa = existing.voiceActors.map { it.peopleId?.toString() ?: it.name }.toMutableSet()
                val mergedVas = existing.voiceActors.toMutableList()
                for (va in voiceActors) {
                    val key = va.peopleId?.toString() ?: va.name
                    if (seenVa.add(key)) {
                        mergedVas.add(va)
                    }
                }
                byId[id] = existing.copy(
                    role = existing.role ?: role,
                    image = existing.image ?: charImg,
                    voiceActors = mergedVas
                )
            }
        }

        val result = byId.values.toList()
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }

    // ── 5. Single Character Details (/character/{id}/_) ──────────────────────

    private data class LinkedRow(
        val id: Int?,
        val name: String,
        val url: String?,
        val image: String?,
        val sub: String?
    )

    private fun parseLinkedTable(table: Element, hrefContains: String): LinkedRow? {
        val link = table.select("a[href*=$hrefContains]").firstOrNull { it.text().trim().isNotEmpty() }
            ?: return null
        val name = link.text().trim()
        if (name.isBlank()) return null
        val url = link.attr("href").trim().takeIf { it.isNotBlank() }
        val imgEl = table.selectFirst("img")
        val image = fullSizeImage(imgEl?.attr("data-src")?.ifBlank { null } ?: imgEl?.attr("src"))
        val cellText = link.closest("td")?.text().orEmpty()
        val sub = cellText
            .replace(name, "")
            .replace(Regex("\\badd\\b", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotBlank() }
        return LinkedRow(id = idFromUrl(url), name = name, url = url, image = image, sub = sub)
    }

    suspend fun getCharacterDetails(characterId: Int): MalCharacterDetails? {
        val cacheKey = "mal:character:$characterId"
        CatalogCache.get<MalCharacterDetails>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/character/$characterId/_") ?: return null
        val doc = Jsoup.parse(html)

        val headerH2 = doc.selectFirst("h2.normal_header") ?: return null
        val nameKanji = headerH2.selectFirst("small")?.text()?.trim()
            ?.removePrefix("(")?.removeSuffix(")")?.takeIf { it.isNotBlank() }
        val headerClone = headerH2.clone()
        headerClone.select("small").remove()
        val name = headerClone.text().trim()
        if (name.isBlank()) return null

        val image = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        val favMatch = Regex("Member Favorites:\\s*([\\d,]+)", RegexOption.IGNORE_CASE).find(doc.body().text())
        val favorites = favMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()

        val container = headerH2.parent()?.clone()
        val spoilers = mutableListOf<String>()
        var about: String? = null
        var note: String? = null

        if (container != null) {
            for (br in container.select("br")) {
                br.replaceWith(org.jsoup.nodes.TextNode("\n"))
            }
            val spoilerSeen = HashSet<String>()
            for (el in container.select("[class*=spoiler]")) {
                val txt = el.text().trim()
                el.remove()
                if (txt.length >= 15 && spoilerSeen.add(txt)) {
                    spoilers.add(txt)
                }
            }

            var rawAbout = container.wholeText()
            rawAbout = rawAbout.replace(Regex("^[\\s\\S]*?\\bCharacters\\b[\\s\\S]*?\\n\\s*\\n+"), "").trim()
            rawAbout = rawAbout.replace(name, "").trim()
            if (nameKanji != null) {
                rawAbout = rawAbout.replace("($nameKanji)", "").replace(nameKanji, "").trim()
            }
            rawAbout = rawAbout.replace(Regex("^(?:\\s*[A-Z][A-Za-z][A-Za-z ]{1,30}:\\s*[^\\n]*\\n?)+"), "").trim()

            val voiceIdx = Regex("Voice Actors", RegexOption.IGNORE_CASE).find(rawAbout)?.range?.first ?: -1
            if (voiceIdx != -1) {
                rawAbout = rawAbout.substring(0, voiceIdx).trim()
            }

            val noteMatch = Regex("\\n*Note:\\s*([\\s\\S]*)$", RegexOption.IGNORE_CASE).find(rawAbout)
            if (noteMatch != null) {
                note = noteMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
                rawAbout = rawAbout.substring(0, noteMatch.range.first).trim()
            }
            about = rawAbout.takeIf { it.isNotBlank() }
        }

        val animeography = mutableListOf<MalCharacterAnime>()
        val voiceActors = mutableListOf<MalVoiceActor>()
        val seenAnime = HashSet<String>()
        val seenVa = HashSet<String>()

        for (table in doc.select("table")) {
            val anime = parseLinkedTable(table, "/anime/")
            if (anime != null) {
                val key = anime.id?.toString() ?: anime.url ?: anime.name
                if (seenAnime.add(key)) {
                    animeography.add(
                        MalCharacterAnime(
                            animeId = anime.id,
                            title = anime.name,
                            url = anime.url,
                            image = anime.image,
                            role = anime.sub
                        )
                    )
                }
                continue
            }
            val va = parseLinkedTable(table, "/people/")
            if (va != null) {
                val vaKey = va.id?.toString() ?: va.url ?: va.name
                if (seenVa.add(vaKey)) {
                    voiceActors.add(
                        MalVoiceActor(
                            peopleId = va.id,
                            name = va.name,
                            url = va.url,
                            image = va.image,
                            language = va.sub
                        )
                    )
                }
            }
        }

        val details = MalCharacterDetails(
            characterId = characterId,
            name = name,
            nameKanji = nameKanji,
            nicknames = emptyList(),
            about = about,
            note = note,
            spoilers = spoilers,
            favorites = favorites,
            image = image,
            animeography = animeography,
            voiceActors = voiceActors
        )
        CatalogCache.set(cacheKey, details, CatalogCache.Bucket.MAPPING)
        return details
    }

    // ── 6. Pictures (/anime/{id}/_/pics & /character/{id}/_/pics) ────────────

    private fun parsePictures(doc: Document): List<MalPicture> {
        val seen = HashSet<String>()
        val pics = mutableListOf<MalPicture>()
        val imgExtRegex = Regex("\\.(jpe?g|png|webp)(\\?|$)", RegexOption.IGNORE_CASE)

        for (a in doc.select("a[href*=cdn.myanimelist.net]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || !imgExtRegex.containsMatchIn(href) || !seen.add(href)) continue
            val imgEl = a.selectFirst("img")
            val thumb = fullSizeImage(imgEl?.attr("data-src")?.ifBlank { null } ?: imgEl?.attr("src"))
            pics.add(MalPicture(image = href, thumbnail = thumb))
        }
        return pics
    }

    suspend fun getAnimePictures(malId: Int): List<MalPicture> {
        val cacheKey = "mal:anime-pics:$malId"
        CatalogCache.get<List<MalPicture>>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/anime/$malId/_/pics") ?: return emptyList()
        val result = parsePictures(Jsoup.parse(html))
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }

    suspend fun getCharacterPictures(characterId: Int): List<MalPicture> {
        val cacheKey = "mal:character-pics:$characterId"
        CatalogCache.get<List<MalPicture>>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/character/$characterId/_/pics") ?: return emptyList()
        val result = parsePictures(Jsoup.parse(html))
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }

    // ── 7. Videos: Trailers & Music Videos (/anime/{id}/_/video) ─────────────

    private fun parseMusicVideoSongs(text: String): List<Pair<String, String>> {
        if (text.isBlank()) return emptyList()
        val songs = mutableListOf<Pair<String, String>>()
        val chunks = text.split(Regex("(?=\"[^\"]+\"\\s*by\\s)"))
        for (chunk in chunks) {
            val m = Regex("^\"([^\"]+)\"\\s*by\\s*([\\s\\S]*)$").find(chunk) ?: continue
            var artist = m.groupValues[2]
            artist = artist.replace(Regex("\\b(?:ED|OP|PV)\\s*\\d+[\\s\\S]*$", RegexOption.IGNORE_CASE), "").trim()
            artist = artist.replace(Regex("play[\\s\\S]*$", RegexOption.IGNORE_CASE), "").trim()
            artist = artist.replace(Regex("[,\\s]+$"), "").trim()
            songs.add(m.groupValues[1].trim() to artist)
        }
        return songs
    }

    private fun extractVideoLinks(blockHtml: String): List<MalVideo> {
        if (blockHtml.isBlank()) return emptyList()
        val doc = Jsoup.parseBodyFragment(blockHtml)
        val out = mutableListOf<MalVideo>()
        val seen = HashSet<String>()
        for (a in doc.select("a[href*=/embed/]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || !seen.add(href)) continue
            val label = a.text().replace(Regex("play\\s*$", RegexOption.IGNORE_CASE), "").trim()
            val ytId = Regex("/embed/([a-zA-Z0-9_-]+)").find(href)?.groupValues?.getOrNull(1)
            out.add(MalVideo(label = label.ifBlank { "Trailer" }, youtubeId = ytId, embedUrl = href))
        }
        return out
    }

    suspend fun getAnimeVideos(malId: Int): MalVideos {
        val cacheKey = "mal:videos:$malId"
        CatalogCache.get<MalVideos>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/anime/$malId/_/video") ?: return MalVideos()

        val mvStartIdx = html.indexOf("Music Videos")
        val mvBlock = if (mvStartIdx == -1) "" else extractBlock(html, "Music Videos", listOf("Add Promotional Video", "Trailers"))

        val trailerSearchFrom = if (mvStartIdx == -1) 0 else mvStartIdx + mvBlock.length
        val trailerStartIdx = html.indexOf("Trailers", trailerSearchFrom)
        val trailerBlock = if (trailerStartIdx == -1) "" else extractBlock(html, "Trailers", listOf("Top Anime"), trailerStartIdx)

        val mvSongs = parseMusicVideoSongs(stripTags(mvBlock))
        val musicVideos = extractVideoLinks(mvBlock).mapIndexed { i, v ->
            val song = mvSongs.getOrNull(i)
            v.copy(songTitle = song?.first, songArtist = song?.second)
        }
        val trailers = extractVideoLinks(trailerBlock)

        val result = MalVideos(musicVideos = musicVideos, trailers = trailers)
        CatalogCache.set(cacheKey, result, CatalogCache.Bucket.MAPPING)
        return result
    }

    // ── 8. Recommendations (/anime/{id}/_/userrecs) ──────────────────────────

    suspend fun getRecommendations(malId: Int): List<MalRecommendation> {
        val cacheKey = "mal:recommendations:$malId"
        CatalogCache.get<List<MalRecommendation>>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/anime/$malId/_/userrecs") ?: return emptyList()

        val recs = mutableListOf<MalRecommendation>()
        val seen = HashSet<Int>()
        val chunks = html.split(Regex("(?=\"Anime: )"))

        for (chunk in chunks) {
            val titleMatch = Regex("^\"Anime:\\s*([^\"]+)\"").find(chunk) ?: continue
            val title = titleMatch.groupValues[1].trim()
            if (title.isBlank()) continue

            val animeId = Regex("/anime/(\\d+)/").find(chunk)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            if (animeId == malId || !seen.add(animeId)) continue

            val imgMatch = Regex("(?:data-src|src)=\"([^\"]+\\.(?:jpg|jpeg|png|webp)[^\"]*)\"", RegexOption.IGNORE_CASE).find(chunk)
            val image = fullSizeImage(imgMatch?.groupValues?.getOrNull(1))

            val nextIdx = chunk.indexOf("\"Anime: ", 1)
            val scoped = if (nextIdx == -1) chunk else chunk.substring(0, nextIdx)
            val votes = Regex("Recommended by").findAll(scoped).count().coerceAtLeast(1)

            recs.add(MalRecommendation(animeId = animeId, title = title, image = image, votes = votes))
            if (recs.size >= RECOMMENDATIONS_LIMIT) break
        }

        CatalogCache.set(cacheKey, recs, CatalogCache.Bucket.MAPPING)
        return recs
    }

    // ── 9. External Links (/anime/{id}/_/external_links) ─────────────────────

    suspend fun getExternalLinks(malId: Int): List<MalLink> {
        val cacheKey = "mal:external:$malId"
        CatalogCache.get<List<MalLink>>(cacheKey)?.let { return it }

        val html = fetchMalHtml("/anime/$malId/_/external_links") ?: return emptyList()
        val doc = Jsoup.parse(html)

        val out = mutableListOf<MalLink>()
        val seen = HashSet<String>()

        for (a in doc.select("a[href^=http]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || href.contains("myanimelist.net")) continue
            if (FOOTER_DOMAINS.any { href.contains(it, ignoreCase = true) }) continue
            if (!seen.add(href)) continue

            val name = a.text().trim().ifBlank {
                runCatching { href.toHttpUrl().host.removePrefix("www.") }.getOrDefault(href)
            }
            out.add(MalLink(name = name, url = href))
        }

        CatalogCache.set(cacheKey, out, CatalogCache.Bucket.MAPPING)
        return out
    }
}
