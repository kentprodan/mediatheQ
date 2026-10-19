package io.github.kentprodan.mediatheq.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import io.github.kentprodan.mediatheq.core.designsystem.R

/** App icons: Material Symbols, rounded, 24 dp (vector drawables in `res/drawable`, see THIRD_PARTY.md). */
object MqIcons {
    val MoreVert: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_more_vert)

    val MusicNote: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.mq_ic_music_note)
}
