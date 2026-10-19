package io.github.kentprodan.mediatheq.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey

/** The bottom navigation tabs, each with its own back stack starting at [rootKey]. [Home] is the start tab. */
enum class TopLevelTab(
    val rootKey: NavKey,
    @param:StringRes val label: Int,
) {
    Home(HomeKey, R.string.tab_home),
    Library(LibraryKey, R.string.tab_library),
    Search(SearchKey, R.string.tab_search),
    Downloads(DownloadsKey, R.string.tab_downloads),
    ;

    /** Filled icon when [selected], so selection is not shown by color alone. */
    @Composable
    fun icon(selected: Boolean): ImageVector =
        when (this) {
            Home -> if (selected) MqIcons.HomeFilled else MqIcons.Home
            Library -> if (selected) MqIcons.LibraryFilled else MqIcons.Library
            Search -> if (selected) MqIcons.SearchFilled else MqIcons.Search
            Downloads -> if (selected) MqIcons.DownloadFilled else MqIcons.Download
        }
}
