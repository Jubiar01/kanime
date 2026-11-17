package com.kanime.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.data.repository.AnimeRepository
import kotlinx.coroutines.Job
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
    val playingEpisode: Pair<Anime, Episode>? = null,
    val searchQuery: String = "",
    val selectedGenre: String = "All",
    val selectedStatus: String = "All",
    val watchlistIds: Set<String> = setOf("fairy_tail_2018", "solo_leveling"),
    val isDarkTheme: Boolean = true,
    val isSearching: Boolean = false,
    val searchResults: List<Anime> = emptyList(),
    val trendingAnime: List<Anime> = AnimeRepository.sampleAnimes.filter { it.isTrending },
    val topAiringAnime: List<Anime> = AnimeRepository.sampleAnimes.filter { it.isTopAiring },
    val seasonalAnime: List<Anime> = AnimeRepository.sampleAnimes.filter { it.isSeasonal },
    val showPluginsScreen: Boolean = false
)

class AnimeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState.asStateFlow()

    private val knownAnimeMap = mutableMapOf<String, Anime>()
    private var searchJob: Job? = null

    val genres: List<String> = AnimeRepository.genres

    init {
        // Cache initial anime
        AnimeRepository.sampleAnimes.forEach { knownAnimeMap[it.id] = it }

        // Fetch live trending and top airing anime from Kitsu API
        viewModelScope.launch {
            try {
                val liveTrending = AnimeRepository.getTrending()
                liveTrending.forEach { knownAnimeMap[it.id] = it }
                val liveAiring = AnimeRepository.getTopAiring()
                liveAiring.forEach { knownAnimeMap[it.id] = it }

                _uiState.update { current ->
                    current.copy(
                        trendingAnime = if (liveTrending.isNotEmpty()) liveTrending else current.trendingAnime,
                        topAiringAnime = if (liveAiring.isNotEmpty()) liveAiring else current.topAiringAnime
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
        _uiState.update { it.copy(currentTab = tab, selectedAnime = null, showPluginsScreen = false) }
    }

    fun openPlugins() {
        _uiState.update { it.copy(showPluginsScreen = true) }
    }

    fun closePlugins() {
        _uiState.update { it.copy(showPluginsScreen = false) }
    }

    fun openAnime(anime: Anime) {
        knownAnimeMap[anime.id] = anime
        _uiState.update { it.copy(selectedAnime = anime) }
    }

    fun closeAnime() {
        _uiState.update { it.copy(selectedAnime = null) }
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
                val results = AnimeRepository.searchOnline(query.trim())
                results.forEach { knownAnimeMap[it.id] = it }
                _uiState.update { it.copy(isSearching = false, searchResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun selectGenre(genre: String) {
        _uiState.update { it.copy(selectedGenre = genre) }
    }

    fun selectStatus(status: String) {
        _uiState.update { it.copy(selectedStatus = status) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }
}
