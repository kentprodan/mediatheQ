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

    val Visibility: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_visibility)

    val VisibilityOff: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_visibility_off)

    val Check: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_check)

    val Sort: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_sort)

    val GridView: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_grid_view)

    val Person: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_person)

    val QueueMusic: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_queue_music)

    val PlayArrow: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_play_arrow)

    val Shuffle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_shuffle)

    val PlayFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_play_arrow_filled)

    val PauseFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_pause_filled)

    /** Media controls keep their direction in right-to-left layouts (Material guidance). */
    val SkipNextFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_skip_next_filled)

    val SkipPreviousFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_skip_previous_filled)

    /** Marks the track that is playing. */
    val GraphicEq: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_graphic_eq)

    val KeyboardArrowDown: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_keyboard_arrow_down)

    /** Mirrors in right-to-left layouts. */
    val ArrowBack: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_arrow_back)

    val DragHandle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_drag_handle)

    val Delete: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_delete)

    val PlayNext: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_queue_play_next)

    val AddToQueue: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_playlist_add)
}
