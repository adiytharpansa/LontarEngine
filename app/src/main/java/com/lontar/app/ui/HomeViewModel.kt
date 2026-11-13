package com.lontar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.core.model.VideoItem
import com.lontar.app.LontarApplication
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val query: String = "",
    val items: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = (application as LontarApplication).backend
    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state = _state.asStateFlow()
    private var request: Job? = null

    init {
        load(query = null)
    }

    fun search(rawQuery: String) {
        val query = rawQuery.trim().takeIf(String::isNotEmpty)
        load(query)
    }

    fun retry() {
        load(_state.value.query.takeIf(String::isNotEmpty))
    }

    private fun load(query: String?) {
        request?.cancel()
        _state.value = HomeUiState(query = query.orEmpty(), isLoading = true)
        request = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    if (query == null) backend.getHomeFeed(page = null)
                    else backend.search(query = query, page = null)
                }
                _state.value = HomeUiState(query = query.orEmpty(), items = page.items)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                _state.value = HomeUiState(
                    query = query.orEmpty(),
                    errorMessage = "Koneksi internet bermasalah. Periksa jaringan lalu coba lagi.",
                )
            } catch (failure: Exception) {
                _state.value = HomeUiState(
                    query = query.orEmpty(),
                    errorMessage = "Video belum bisa dimuat. Coba lagi sebentar.",
                )
            }
        }
    }
}
