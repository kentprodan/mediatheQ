package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import io.github.kentprodan.mediatheq.core.navigation.MoviesKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesListKey
import io.github.kentprodan.mediatheq.core.navigation.VideoCollectionKey
import io.github.kentprodan.mediatheq.core.navigation.VideoCollectionsKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey

/** Registers the TV video grids and collections (#426); a poster opens the movie or series page (#427, #428). */
fun EntryProviderScope<NavKey>.tvVideoEntries(navigator: Navigator) {
    entry<MoviesKey> {
        TvMoviesRoute({ navigator.navigateTo(MovieKey(it)) }) { navigator.navigateTo(VideoCollectionsKey) }
    }
    entry<SeriesListKey> { TvSeriesRoute { navigator.navigateTo(SeriesKey(it)) } }
    entry<VideoCollectionsKey> { TvCollectionsRoute { navigator.navigateTo(VideoCollectionKey(it)) } }
    entry<VideoCollectionKey> { key -> TvCollectionRoute(key.id) { navigator.navigateTo(MovieKey(it)) } }
    entry<SeriesKey> { key ->
        TvSeriesPageRoute(key.id) { id, start -> navigator.navigateTo(VideoPlayerKey(id, start.inWholeMilliseconds)) }
    }
    entry<MovieKey> { key ->
        TvMovieRoute(key.id, onOpenMovie = { navigator.navigateTo(MovieKey(it)) }) { id, start ->
            navigator.navigateTo(VideoPlayerKey(id, start.inWholeMilliseconds))
        }
    }
    entry<VideoPlayerKey> { key -> TvPlayerRoute(key) { navigator.back() } }
}
