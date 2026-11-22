package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import io.github.kentprodan.mediatheq.core.navigation.MoviesKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PersonKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesListKey
import io.github.kentprodan.mediatheq.core.navigation.VideoCollectionKey
import io.github.kentprodan.mediatheq.core.navigation.VideoGroupKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey

/** Registers the TV video grids and collections (#426); a poster opens the movie or series page (#427, #428). */
fun EntryProviderScope<NavKey>.tvVideoEntries(navigator: Navigator) {
    val onOpenGroup = { kind: TvVideoKind, studio: Boolean, name: String ->
        navigator.navigateTo(VideoGroupKey(kind == TvVideoKind.Series, studio, name))
    }
    val onOpenCollection = { id: MediaId -> navigator.navigateTo(VideoCollectionKey(id)) }
    entry<MoviesKey> {
        TvMoviesRoute(TvVideoNavigation({ navigator.navigateTo(MovieKey(it)) }, onOpenCollection, onOpenGroup))
    }
    entry<SeriesListKey> {
        TvSeriesRoute(TvVideoNavigation({ navigator.navigateTo(SeriesKey(it)) }, onOpenCollection, onOpenGroup))
    }
    // A genre's or studio's grid (#522), titled with its name.
    entry<VideoGroupKey> { key ->
        val kind = if (key.series) TvVideoKind.Series else TvVideoKind.Movies
        val only = if (key.studio) TvVideoOnly.Studio(key.name) else TvVideoOnly.Genre(key.name)
        TvVideosRoute(kind, only, {
            if (key.series) navigator.navigateTo(SeriesKey(it)) else navigator.navigateTo(MovieKey(it))
        })
    }
    entry<VideoCollectionKey> { key -> TvCollectionRoute(key.id) { navigator.navigateTo(MovieKey(it)) } }
    entry<SeriesKey> { key ->
        TvSeriesPageRoute(key.id) { id, start -> navigator.navigateTo(VideoPlayerKey(id, start.inWholeMilliseconds)) }
    }
    entry<MovieKey> { key ->
        TvMovieRoute(
            key.id,
            onOpenMovie = { navigator.navigateTo(MovieKey(it)) },
            onOpenPerson = { id, name -> navigator.navigateTo(PersonKey(id, name)) },
        ) { id, start ->
            navigator.navigateTo(VideoPlayerKey(id, start.inWholeMilliseconds))
        }
    }
    entry<PersonKey> { key ->
        TvPersonRoute(key, { navigator.navigateTo(MovieKey(it)) }) { navigator.navigateTo(SeriesKey(it)) }
    }
    entry<VideoPlayerKey> { key -> TvPlayerRoute(key) { navigator.back() } }
}
