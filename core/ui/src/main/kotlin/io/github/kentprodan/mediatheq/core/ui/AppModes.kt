package io.github.kentprodan.mediatheq.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.AppMode

/** An app mode's name (ADR 0051): the Home toggle, onboarding and Settings. */
@get:StringRes
val AppMode.label: Int
    get() =
        when (this) {
            AppMode.Music -> R.string.core_ui_app_mode_music
            AppMode.Video -> R.string.core_ui_app_mode_video
            AppMode.Both -> R.string.core_ui_app_mode_both
        }

/** One sentence on what an app mode shows. */
@get:StringRes
val AppMode.description: Int
    get() =
        when (this) {
            AppMode.Music -> R.string.core_ui_app_mode_music_description
            AppMode.Video -> R.string.core_ui_app_mode_video_description
            AppMode.Both -> R.string.core_ui_app_mode_both_description
        }

/** An app mode's icon. */
val AppMode.icon: ImageVector
    @Composable get() =
        when (this) {
            AppMode.Music -> MqIcons.MusicNote
            AppMode.Video -> MqIcons.Movie
            AppMode.Both -> MqIcons.Library
        }
