package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Settings -> About -> Changelog (#151). [changelog] reads the file the build bundled (it lives in the app); it is
 * read and parsed once, off the main thread. The newest version is open; the others open on a tap.
 */
class ChangelogViewModel(
    changelog: () -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /** The versions toggled away from their start (the newest open, the rest closed). */
    private val toggled = MutableStateFlow(emptySet<String>())

    val uiState: StateFlow<ChangelogUiState> =
        combine(flow { emit(parseChangelog(changelog())) }.flowOn(dispatcher), toggled) { versions, toggled ->
            val newest = versions.firstOrNull()?.name
            ChangelogUiState.Content(
                versions,
                versions.map { it.name }.filter { (it == newest) != (it in toggled) }.toSet(),
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, ChangelogUiState.Loading)

    fun onAction(action: ChangelogAction) {
        when (action) {
            is ChangelogAction.Toggle -> {
                toggled.update {
                    if (action.name in
                        it
                    ) {
                        it - action.name
                    } else {
                        it + action.name
                    }
                }
            }
        }
    }
}
