package com.yzp.videolearning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CatalogUiState(
    val categories: List<CourseCategory> = emptyList(),
    val progress: Map<String, LearningProgress> = emptyMap(),
    val error: String? = null,
)

class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = CourseCatalogRepository(application)
    private val progressRepository = LearningProgressRepository(application)
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = runCatching { withContext(Dispatchers.IO) { catalog.load() } }
                .getOrElse {
                    _uiState.value = CatalogUiState(error = "课程目录加载失败，请重新打开应用。")
                    return@launch
                }
            // Publish routes only after the first persisted progress value is ready.
            // A restored playing route must not start at zero and overwrite saved progress.
            progressRepository.progress.collectLatest { progress ->
                _uiState.value = CatalogUiState(categories = categories, progress = progress)
            }
        }
    }

    fun markOpened(videoId: String) {
        viewModelScope.launch { progressRepository.markOpened(videoId) }
    }

    fun setCompleted(videoId: String, completed: Boolean) {
        viewModelScope.launch { progressRepository.setCompleted(videoId, completed) }
    }

    fun savePosition(videoId: String, positionMs: Long) {
        viewModelScope.launch { progressRepository.savePosition(videoId, positionMs) }
    }
}
