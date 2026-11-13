package io.github.kentprodan.mediatheq.tv.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey
import io.github.kentprodan.mediatheq.tv.R

/** The drawer's entries, top to bottom (ADR 0041). */
enum class TvDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
) {
    Home(HomeKey, R.string.tv_nav_home),
    Library(LibraryKey, R.string.tv_nav_library),
    Search(SearchKey, R.string.tv_nav_search),
    NowPlaying(NowPlayingKey, R.string.tv_nav_now_playing),
    Settings(SettingsKey, R.string.tv_nav_settings),
    ;

    val icon: ImageVector
        @Composable get() =
            when (this) {
                Home -> MqTvIcons.Home
                Library -> MqTvIcons.Library
                Search -> MqTvIcons.Search
                NowPlaying -> MqTvIcons.NowPlaying
                Settings -> MqTvIcons.Settings
            }

    companion object {
        fun of(key: NavKey): TvDestination? = entries.firstOrNull { it.key == key }
    }
}
