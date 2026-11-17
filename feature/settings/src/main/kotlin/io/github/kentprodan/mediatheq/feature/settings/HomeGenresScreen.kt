package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.data.genreCounts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One genre of Settings -> Home page -> Genre rows (#441): how many items have it and whether it has a row. */
data class HomeGenre(
    val name: String,
    val count: Int,
    val shown: Boolean,
)

/** The library's genres (the most common first) and the ones picked for Home, of the active account (#441). */
@HiltViewModel
class HomeGenresViewModel
    @Inject
    constructor(
        private val preferences: LibraryPreferencesRepository,
        videos: VideoRepository,
    ) : ViewModel() {
        /** `null` until read. */
        val genres: StateFlow<List<HomeGenre>?> =
            combine(
                videos.movies(VideoSort.Name, unwatched = false),
                videos.series(VideoSort.Name, unwatched = false),
                preferences.homeGenres,
            ) { movies, series, picked ->
                genreCounts(movies.map { it.genres } + series.map { it.genres }).map { (name, count) ->
                    HomeGenre(name, count, name in picked)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** A genre's row on or off; a new one goes last. */
        fun toggle(genre: String) {
            viewModelScope.launch {
                val picked = preferences.homeGenres.first()
                preferences.setHomeGenres(if (genre in picked) picked - genre else picked + genre)
            }
        }
    }

@Composable
internal fun HomeGenresRoute(
    onBack: () -> Unit,
    viewModel: HomeGenresViewModel = hiltViewModel(),
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    HomeGenresScreen(genres.orEmpty(), viewModel::toggle, onBack)
}

/** Settings -> Home page -> Genre rows (#441): a switch per genre; the picked ones show on Home in the order picked. */
@Composable
fun HomeGenresScreen(
    genres: List<HomeGenre>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.settings_home_genres), onBack) {
        item(key = "intro") {
            Text(
                stringResource(R.string.settings_home_genres_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (genres.isNotEmpty()) {
            item(key = "genres") {
                Group(R.string.settings_home_genres_group) {
                    genres.forEach { genre ->
                        item { shapes ->
                            SwitchItem(
                                shapes,
                                genre.name,
                                pluralStringResource(R.plurals.settings_home_genre_count, genre.count, genre.count),
                                genre.shown,
                            ) { onToggle(genre.name) }
                        }
                    }
                }
            }
        }
    }
}
