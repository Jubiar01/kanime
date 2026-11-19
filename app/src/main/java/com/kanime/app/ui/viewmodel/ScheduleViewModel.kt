package com.kanime.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanime.app.data.api.AniListApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val entries: List<AniListApi.AniListAiringEntry> = emptyList(),
    /** Local midnight of the first day of the 7-day window. */
    val windowStartMs: Long = 0L
)

/** Loads the 7-day airing schedule for the Schedule tab. Survives tab switches (Activity scoped). */
class ScheduleViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastLoadedMs = 0L

    init {
        load()
    }

    /** Reload when the data is older than 15 minutes (called whenever the Schedule tab is opened). */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastLoadedMs > 15L * 60L * 1000L) load()
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 7)
            val endMs = cal.timeInMillis

            _state.update { it.copy(isLoading = true, hasError = false, windowStartMs = startMs) }
            val entries = try {
                AniListApi.getAiringSchedule(startMs / 1000L, endMs / 1000L)
            } catch (_: Exception) {
                null
            }
            if (entries != null) lastLoadedMs = System.currentTimeMillis()
            _state.update {
                if (entries == null) it.copy(isLoading = false, hasError = true)
                else it.copy(isLoading = false, hasError = false, entries = entries)
            }
        }
    }
}
