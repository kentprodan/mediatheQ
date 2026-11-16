package io.github.kentprodan.mediatheq.tv.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.MoviesKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesListKey
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey
import io.github.kentprodan.mediatheq.tv.R

/**
 * The drawer's entries, top to bottom (ADR 0041; Movies and Series before the music, #426). [video] and [music] say
 * which app mode shows the entry; the others always show.
 */
enum class TvDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    val video: Boolean = false,
    val music: Boolean = false,
) {
    Home(HomeKey, R.string.tv_nav_home),
    Search(SearchKey, R.string.tv_nav_search),
    Movies(MoviesKey, R.string.tv_nav_movies, video = true),
    Series(SeriesListKey, R.string.tv_nav_series, video = true),
    Library(LibraryKey, R.string.tv_nav_library, music = true),
    NowPlaying(NowPlayingKey, R.string.tv_nav_now_playing, music = true),
    Settings(SettingsKey, R.string.tv_nav_settings),
    ;

    val icon: ImageVector
        @Composable get() =
            when (this) {
                Home -> MqTvIcons.Home
                Library -> MqTvIcons.Library
                Search -> MqTvIcons.Search
                Movies -> MqTvIcons.Movie
                Series -> MqTvIcons.Series
                NowPlaying -> MqTvIcons.NowPlaying
                Settings -> MqTvIcons.Settings
            }

    companion object {
        fun of(key: NavKey): TvDestination? = entries.firstOrNull { it.key == key }

        /** The entries [mode] shows (ADR 0051 on TV, #426). */
        fun shownIn(mode: AppMode): List<TvDestination> =
            entries.filter { (!it.video || mode.video) && (!it.music || mode.music) }
    }
}
