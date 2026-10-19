package io.github.kentprodan.mediatheq.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import io.github.kentprodan.mediatheq.core.designsystem.R

/**
 * App icons: Material Symbols, rounded, 24 dp (vector drawables in `res/drawable`, see THIRD_PARTY.md).
 * `*Filled` variants mark the selected state, so selection is never shown by color alone.
 */
object MqIcons {
    val MoreVert: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_more_vert)

    val MusicNote: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_music_note)

    val Home: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_home)

    val HomeFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_home_filled)

    val Library: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_library_music)

    val LibraryFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_library_music_filled)

    val Search: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_search)

    val SearchFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_search_filled)

    val Download: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_download)

    val DownloadFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_download_filled)
}
