package com.lontar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.core.model.VideoDetails
import com.lontar.app.LontarApplication
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoDetailsUiState(
    val details: VideoDetails? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class VideoDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = (application as LontarApplication).backend
    private val _state = MutableStateFlow(VideoDetailsUiState())
    val state = _state.asStateFlow()
    private var currentVideoId: String? = null

    fun load(videoId: String, force: Boolean = false) {
        if (!force && currentVideoId == videoId && (_state.value.isLoading || _state.value.details != null)) {
            return
        }
        currentVideoId = videoId
        _state.value = VideoDetailsUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val details = withContext(Dispatchers.IO) {
                    backend.getVideoDetails(videoId)
                }
                _state.value = VideoDetailsUiState(details = details)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                _state.value = VideoDetailsUiState(
                    errorMessage = "Koneksi internet bermasalah. Periksa jaringan lalu coba lagi.",
                )
            } catch (failure: Exception) {
                _state.value = VideoDetailsUiState(
                    errorMessage = "Detail video belum bisa dimuat. Coba lagi sebentar.",
                )
            }
        }
    }
}
