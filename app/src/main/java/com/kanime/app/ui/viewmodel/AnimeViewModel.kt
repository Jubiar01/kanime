package com.kanime.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanime.app.data.api.AnimeUpscalerMode
import com.kanime.app.data.api.CatalogCache
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.AnimeExtras
import com.kanime.app.data.model.Episode
import com.kanime.app.data.model.MalCharacterDetails
import com.kanime.app.data.model.MalPicture
import com.kanime.app.data.repository.AnimeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavTab {
    HOME, EXPLORE, WATCHLIST, SETTINGS
}

data class AnimeUiState(
    val currentTab: NavTab = NavTab.HOME,
    val selectedAnime: Anime? = null,
    val isLoadingDetails: Boolean = false,
    val isLoadingCatalogEpisodes: Boolean = false,
    val selectedAnimeExtras: AnimeExtras = AnimeExtras(),
    val isLoadingExtras: Boolean = false,
    val selectedCharacter: MalCharacterDetails? = null,
    val selectedCharacterPictures: List<MalPicture> = emptyList(),
    val isLoadingCharacter: Boolean = false,
    val showCharacterSheet: Boolean = false,
    val playingEpisode: Pair<Anime, Episode>? = null,
    val searchQuery: String = "",
    val searchSource: String = "AniList",
    val selectedGenre: String = "All",
    val selectedStatus: String = "All",
    val watchlistIds: Set<String> = setOf("fairy_tail_2018", "solo_leveling"),
    val isDarkTheme: Boolean = true,
    val isSearching: Boolean = false,
    val isBrowsingCatalog: Boolean = false,
    val searchResults: List<Anime> = emptyList(),
    val exploreCatalog: List<Anime> = emptyList(),
    val trendingAnime: List<Anime> = AnimeRepository.sampleAnimes.filter { it.isTrending },
    val topAiringAnime: List<Anime> = AnimeRepository.sampleAnimes.filter { it.isTopAiring },
    val seasonalAnime: List<Anime> = AnimeRepository.sampleAnimes.filter { it.isSeasonal },
    val showPluginsScreen: Boolean = false
)

class AnimeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState.asStateFlow()

    private val knownAnimeMap = LinkedHashMap<String, Anime>()
    private var searchJob: Job? = null
    private var browseJob: Job? = null
    private var detailJob: Job? = null
    private var extrasJob: Job? = null
    private var characterJob: Job? = null

    val genres: List<String> = AnimeRepository.genres

    init {
        // Seed local offline fallback anime
        AnimeRepository.sampleAnimes.forEach { knownAnimeMap[it.id] = it }

        // Fetch live Trending, Top Airing, Current Season & Explore catalog in parallel
        // (AniList GraphQL primary -> Kitsu fallback -> sample fallback)
        viewModelScope.launch {
            try {
                val trendingDef = async { AnimeRepository.getTrending() }
                val airingDef = async { AnimeRepository.getTopAiring() }
                val seasonalDef = async { AnimeRepository.getSeasonal() }

                val liveTrending = trendingDef.await()
                val liveAiring = airingDef.await()
                val liveSeasonal = seasonalDef.await()

                liveTrending.forEach { knownAnimeMap[it.id] = it }
                liveAiring.forEach { knownAnimeMap[it.id] = it }
                liveSeasonal.forEach { knownAnimeMap[it.id] = it }

                val combinedExplore = (liveTrending + liveSeasonal + liveAiring)
                    .distinctBy { it.id }

                _uiState.update { current ->
                    current.copy(
                        trendingAnime = if (liveTrending.isNotEmpty()) liveTrending else current.trendingAnime,
                        topAiringAnime = if (liveAiring.isNotEmpty()) liveAiring else current.topAiringAnime,
                        seasonalAnime = if (liveSeasonal.isNotEmpty()) liveSeasonal else current.seasonalAnime,
                        exploreCatalog = if (combinedExplore.isNotEmpty()) combinedExplore else current.exploreCatalog
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val allAnime: List<Anime>
        get() = knownAnimeMap.values.toList()

    fun selectTab(tab: NavTab) {
        _uiState.update {
            it.copy(
                currentTab = tab,
                selectedAnime = null,
                showPluginsScreen = false,
                showCharacterSheet = false
            )
        }
    }

    fun openPlugins() {
        _uiState.update { it.copy(showPluginsScreen = true) }
    }

    fun closePlugins() {
        _uiState.update { it.copy(showPluginsScreen = false) }
    }

    fun openAnime(anime: Anime) {
        val cached = knownAnimeMap[anime.id]
        val initial = if (cached != null && cached.isDetailsLoaded) cached else anime
        knownAnimeMap[initial.id] = initial

        detailJob?.cancel()
        extrasJob?.cancel()
        characterJob?.cancel()

        _uiState.update {
            it.copy(
                selectedAnime = initial,
                isLoadingDetails = !initial.isDetailsLoaded,
                isLoadingCatalogEpisodes = true,
                selectedAnimeExtras = AnimeExtras(
                    streamingPlatforms = initial.streamingPlatforms,
                    externalLinks = initial.externalLinks
                ),
                isLoadingExtras = false,
                selectedCharacter = null,
                selectedCharacterPictures = emptyList(),
                showCharacterSheet = false
            )
        }

        detailJob = viewModelScope.launch {
            try {
                // 1) Enrich full MAL details + TMDB/Kitsu/AniList art fallback chain
                val enriched = if (initial.isDetailsLoaded) {
                    initial
                } else {
                    AnimeRepository.enrichAnimeDetails(initial)
                }
                knownAnimeMap[enriched.id] = enriched
                _uiState.update { current ->
                    if (current.selectedAnime?.id == initial.id) {
                        current.copy(
                            selectedAnime = enriched,
                            isLoadingDetails = false,
                            selectedAnimeExtras = current.selectedAnimeExtras.copy(
                                streamingPlatforms = enriched.streamingPlatforms.ifEmpty { current.selectedAnimeExtras.streamingPlatforms }
                            )
                        )
                    } else current
                }

                // 2) Resolve episodes via MAL + TMDB -> Kitsu -> AniList stills/titles
                val resolvedEpisodes = AnimeRepository.getResolvedEpisodes(enriched)
                if (resolvedEpisodes.isNotEmpty()) {
                    val withEps = enriched.copy(episodeList = resolvedEpisodes)
                    knownAnimeMap[withEps.id] = withEps
                    _uiState.update { current ->
                        if (current.selectedAnime?.id == initial.id) {
                            current.copy(
                                selectedAnime = withEps,
                                isLoadingCatalogEpisodes = false
                            )
                        } else current
                    }
                } else {
                    _uiState.update { current ->
                        if (current.selectedAnime?.id == initial.id) {
                            current.copy(isLoadingCatalogEpisodes = false)
                        } else current
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { current ->
                    if (current.selectedAnime?.id == initial.id) {
                        current.copy(isLoadingDetails = false, isLoadingCatalogEpisodes = false)
                    } else current
                }
            }
        }
    }

    fun openAnimeByMalId(malId: Int, fallbackTitle: String, fallbackImage: String? = null) {
        val existing = knownAnimeMap["mal_$malId"] ?: knownAnimeMap.values.firstOrNull { it.malId == malId }
        if (existing != null) {
            openAnime(existing)
            return
        }
        val placeholder = Anime(
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
            coverUrl = fallbackImage ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=60",
            bannerUrl = fallbackImage ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=60",
            malId = malId
        )
        openAnime(placeholder)
    }

    fun refreshSelectedAnime() {
        val current = _uiState.value.selectedAnime ?: return
        val reset = current.copy(isDetailsLoaded = false)
        knownAnimeMap[reset.id] = reset
        openAnime(reset)
    }

    fun loadCharactersForSelected() {
        val anime = _uiState.value.selectedAnime ?: return
        val malId = anime.malId ?: return
        if (_uiState.value.selectedAnimeExtras.charactersLoaded) return

        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingExtras = true) }
            try {
                val chars = AnimeRepository.getCharacters(malId)
                _uiState.update { current ->
                    if (current.selectedAnime?.id == anime.id) {
                        current.copy(
                            isLoadingExtras = false,
                            selectedAnimeExtras = current.selectedAnimeExtras.copy(
                                characters = chars,
                                charactersLoaded = true
                            )
                        )
                    } else current
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingExtras = false) }
            }
        }
    }

    fun loadMediaForSelected() {
        val anime = _uiState.value.selectedAnime ?: return
        val malId = anime.malId ?: return
        if (_uiState.value.selectedAnimeExtras.mediaLoaded) return

        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingExtras = true) }
            try {
                val (themes, videos, pictures) = AnimeRepository.loadMediaExtras(malId)
                _uiState.update { current ->
                    if (current.selectedAnime?.id == anime.id) {
                        current.copy(
                            isLoadingExtras = false,
                            selectedAnimeExtras = current.selectedAnimeExtras.copy(
                                themes = themes,
                                videos = videos,
                                pictures = pictures,
                                mediaLoaded = true
                            )
                        )
                    } else current
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingExtras = false) }
            }
        }
    }

    fun loadMoreForSelected() {
        val anime = _uiState.value.selectedAnime ?: return
        val malId = anime.malId ?: return
        if (_uiState.value.selectedAnimeExtras.moreLoaded) return

        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingExtras = true) }
            try {
                val (recs, external, streaming) = AnimeRepository.loadMoreExtras(malId)
                _uiState.update { current ->
                    if (current.selectedAnime?.id == anime.id) {
                        current.copy(
                            isLoadingExtras = false,
                            selectedAnimeExtras = current.selectedAnimeExtras.copy(
                                recommendations = recs,
                                externalLinks = external,
                                streamingPlatforms = streaming.ifEmpty { current.selectedAnimeExtras.streamingPlatforms },
                                moreLoaded = true
                            )
                        )
                    } else current
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingExtras = false) }
            }
        }
    }

    fun openCharacter(characterId: Int) {
        characterJob?.cancel()
        _uiState.update {
            it.copy(
                showCharacterSheet = true,
                isLoadingCharacter = true,
                selectedCharacter = null,
                selectedCharacterPictures = emptyList()
            )
        }
        characterJob = viewModelScope.launch {
            try {
                val (details, pics) = AnimeRepository.getCharacterDetails(characterId)
                _uiState.update {
                    it.copy(
                        selectedCharacter = details,
                        selectedCharacterPictures = pics,
                        isLoadingCharacter = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingCharacter = false) }
            }
        }
    }

    fun closeCharacter() {
        characterJob?.cancel()
        _uiState.update { it.copy(showCharacterSheet = false) }
    }

    fun closeAnime() {
        detailJob?.cancel()
        extrasJob?.cancel()
        characterJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAnime = null,
                showCharacterSheet = false
            )
        }
    }

    fun playEpisode(anime: Anime, episode: Episode) {
        _uiState.update { it.copy(playingEpisode = anime to episode) }
    }

    fun closePlayer() {
        _uiState.update { it.copy(playingEpisode = null) }
    }

    fun toggleWatchlist(animeId: String) {
        _uiState.update { current ->
            val updated = current.watchlistIds.toMutableSet()
            if (updated.contains(animeId)) {
                updated.remove(animeId)
            } else {
                updated.add(animeId)
            }
            current.copy(watchlistIds = updated)
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // Debounce typing
            _uiState.update { it.copy(isSearching = true) }
            try {
                val res = AnimeRepository.searchOnlineWithSource(query.trim())
                res.results.forEach { knownAnimeMap[it.id] = it }
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = res.results,
                        searchSource = res.source
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun selectGenre(genre: String) {
        _uiState.update { it.copy(selectedGenre = genre) }
        refreshExploreCatalog()
    }

    fun selectStatus(status: String) {
        _uiState.update { it.copy(selectedStatus = status) }
        refreshExploreCatalog()
    }

    private fun refreshExploreCatalog() {
        val genre = _uiState.value.selectedGenre
        val status = _uiState.value.selectedStatus
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            _uiState.update { it.copy(isBrowsingCatalog = true) }
            try {
                val items = AnimeRepository.browseCatalog(genre = genre, status = status)
                items.forEach { knownAnimeMap[it.id] = it }
                _uiState.update { it.copy(exploreCatalog = items, isBrowsingCatalog = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBrowsingCatalog = false) }
            }
        }
    }

    fun saveTmdbApiKey(apiKey: String) {
        CatalogConfig.setTmdbApiKey(apiKey)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        CatalogConfig.setAutoPlayNext(enabled)
    }

    fun setAnimeUpscalerMode(mode: AnimeUpscalerMode) {
        CatalogConfig.setAnimeUpscalerMode(mode)
    }

    fun setAnimeLineSharpening(enabled: Boolean) {
        CatalogConfig.setAnimeLineSharpening(enabled)
    }

    fun setAnimeLineSharpeningStrength(strength: Float) {
        CatalogConfig.setAnimeLineSharpeningStrength(strength)
    }

    fun clearCatalogCache() {
        CatalogCache.clear()
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }
}
