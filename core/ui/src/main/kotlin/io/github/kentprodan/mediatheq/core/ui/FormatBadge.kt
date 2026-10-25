package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.SpatialFormat

/** The kind of audio a track is, shown as a small badge in track lists. */
sealed interface FormatBadge {
    data object DolbyAtmos : FormatBadge

    data object DtsX : FormatBadge

    data class Surround(
        val channels: Int,
    ) : FormatBadge

    data object Stereo : FormatBadge

    data object Mono : FormatBadge
}

/** The badge for this format: spatial audio first, then by channels; `null` when the server did not say. */
fun AudioFormat.formatBadge(): FormatBadge? {
    val channels = channels
    return when {
        spatialFormat == SpatialFormat.DolbyAtmos -> FormatBadge.DolbyAtmos
        spatialFormat == SpatialFormat.DtsX -> FormatBadge.DtsX
        channels == null -> null
        channels > 2 -> FormatBadge.Surround(channels)
        channels == 2 -> FormatBadge.Stereo
        channels == 1 -> FormatBadge.Mono
        else -> null
    }
}

/** The badge as an icon in the content color, named for TalkBack ("Stereo", "Surround 5.1", "Dolby Atmos"). */
@Composable
fun FormatBadgeIcon(
    badge: FormatBadge,
    modifier: Modifier = Modifier,
) {
    Icon(badge.icon(), contentDescription = badge.label(), modifier = modifier.size(MediatheqSizes.formatBadge))
}

@Composable
private fun FormatBadge.icon(): ImageVector =
    when (this) {
        FormatBadge.DolbyAtmos -> MqIcons.FormatDolbyAtmos
        FormatBadge.DtsX, is FormatBadge.Surround -> MqIcons.FormatSurround
        FormatBadge.Stereo -> MqIcons.FormatStereo
        FormatBadge.Mono -> MqIcons.FormatMono
    }

@Composable
private fun FormatBadge.label(): String =
    when (this) {
        FormatBadge.DolbyAtmos -> {
            stringResource(R.string.core_ui_format_dolby_atmos)
        }

        FormatBadge.DtsX -> {
            stringResource(R.string.core_ui_format_dts_x)
        }

        is FormatBadge.Surround -> {
            when (channels) {
                6 -> stringResource(R.string.core_ui_format_surround_5_1)
                8 -> stringResource(R.string.core_ui_format_surround_7_1)
                else -> pluralStringResource(R.plurals.core_ui_format_channels, channels, channels)
            }
        }

        FormatBadge.Stereo -> {
            stringResource(R.string.core_ui_format_stereo)
        }

        FormatBadge.Mono -> {
            stringResource(R.string.core_ui_format_mono)
        }
    }
