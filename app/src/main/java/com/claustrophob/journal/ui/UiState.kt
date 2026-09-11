package com.claustrophob.journal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.container
import com.claustrophob.journal.data.Res
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class UiState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val hasData: Boolean get() = data != null
    // Пустой экран показываем, только когда рисовать реально нечего.
    val showSkeleton: Boolean get() = loading && data == null
}

// Тянет данные из репозитория: сначала снимок из кэша, потом свежее.
// Ошибка не стирает то, что уже нарисовано, — уходит в плашку сверху.
open class LoaderViewModel<T>(
    private val source: () -> Flow<Res<T>>,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState<T>(loading = true))
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        job?.cancel()
        _state.value = _state.value.copy(
            loading = !refresh && _state.value.data == null,
            refreshing = refresh,
            error = null,
        )
        job = viewModelScope.launch {
            source().collect { result ->
                _state.value = when (result) {
                    is Res.Ok -> UiState(
                        data = result.data,
                        loading = false,
                        refreshing = false,
                        error = null,
                        stale = result.stale,
                        updatedAt = result.savedAt,
                    )

                    is Res.Err -> _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}

@Composable
inline fun <reified VM : ViewModel> journalViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val appContainer = LocalContext.current.container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(appContainer) } },
    )
}
