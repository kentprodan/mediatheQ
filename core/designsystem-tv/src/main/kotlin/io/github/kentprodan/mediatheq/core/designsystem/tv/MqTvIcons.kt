package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/** The TV app's icons (ADR 0037): the phone's Material Symbols, copied by value like the other tokens. */
object MqTvIcons {
    val Home: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_home)
    val Library: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_library_music)
    val Search: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_search)
    val NowPlaying: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_queue_music)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_settings)
    val Play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_play_arrow_filled)
    val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_pause_filled)
    val MusicNote: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_music_note)
    val Person: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_person)
    val Shuffle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_shuffle)
    val Radio: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_radio)
    val Add: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_add)
    val Favorite: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_favorite)
    val FavoriteFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_favorite_filled)
    val Resume: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_resume)
    val PlayNext: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_queue_play_next)
    val Playing: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tv_ic_graphic_eq)
}
