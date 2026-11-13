package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel

/**
 * An album's or track's quality as a chip (#238): "Dolby Atmos", "Hi-Res Lossless · FLAC · 24/96", "Lossless", or a
 * lossy file's codec and bitrate. Atmos stands out in the primary colors; the rest is tonal.
 */
@Composable
fun MqTvFormatBadge(
    quality: QualityLabel,
    modifier: Modifier = Modifier,
) {
    val atmos = quality.kind == QualityKind.DolbyAtmos
    val name = stringResource(quality.kind.text)
    val text =
        when (quality.kind) {
            QualityKind.HiResLossless -> "$name · ${quality.detail}"
            QualityKind.HighQuality, QualityKind.HighEfficiency -> quality.detail.ifEmpty { name }
            else -> name
        }
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        modifier
            .background(if (atmos) colors.primary else colors.secondaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (atmos) colors.onPrimary else colors.onSecondaryContainer,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/** The kind's name ("Hi-Res Lossless"), as on the phone. */
val QualityKind.text: Int
    get() =
        when (this) {
            QualityKind.DolbyAtmos -> R.string.core_ui_tv_quality_atmos
            QualityKind.HiResLossless -> R.string.core_ui_tv_quality_hi_res
            QualityKind.Lossless -> R.string.core_ui_tv_quality_lossless
            QualityKind.HighQuality -> R.string.core_ui_tv_quality_high
            QualityKind.HighEfficiency -> R.string.core_ui_tv_quality_efficient
        }
