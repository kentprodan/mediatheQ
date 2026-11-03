package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqProgressFallback
import io.github.kentprodan.mediatheq.core.designsystem.component.MqWaveformProgress
import io.github.kentprodan.mediatheq.core.designsystem.component.MqWaveformStyle
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A fixed track shape: a quiet intro, a build-up, loud choruses, a quiet bridge and a fade-out. */
private val previewLevels =
    List(200) { i ->
        val envelope =
            when {
                i < 20 -> 0.15f + i * 0.01f
                i in 110..130 -> 0.3f
                i > 180 -> (200 - i) * 0.04f
                else -> 0.7f
            }
        (envelope + 0.25f * abs(sin(i * PI / 7).toFloat())).coerceIn(0f, 1f)
    }

/** One look at 0, 40 and 100 % played; [levels] `null` shows [fallback]. */
@Composable
private fun AtThreePositions(
    levels: List<Float>?,
    style: MqWaveformStyle = MqWaveformStyle.Sharp,
    fallback: MqProgressFallback = MqProgressFallback.Basic,
) {
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                listOf(0f, 0.4f, 1f).forEach {
                    MqWaveformProgress(levels, it, "", onSeek = {}, style = style, fallback = fallback)
                }
            }
        }
    }
}

@PreviewTest
@MqPreviews
@Composable
fun WaveformSharpScreenshot() = AtThreePositions(previewLevels, MqWaveformStyle.Sharp)

@PreviewTest
@MqPreviews
@Composable
fun WaveformSmoothScreenshot() = AtThreePositions(previewLevels, MqWaveformStyle.Smooth)

@PreviewTest
@MqPreviews
@Composable
fun WaveformBarsScreenshot() = AtThreePositions(previewLevels, MqWaveformStyle.Bars)

@PreviewTest
@MqPreviews
@Composable
fun WaveformBarsReflectedScreenshot() = AtThreePositions(previewLevels, MqWaveformStyle.BarsReflected)

/** No waveform yet: the plain slider. */
@PreviewTest
@MqPreviews
@Composable
fun WaveformFallbackBasicScreenshot() = AtThreePositions(null, fallback = MqProgressFallback.Basic)

/** No waveform yet: the wavy progress. */
@PreviewTest
@MqPreviews
@Composable
fun WaveformFallbackWavyScreenshot() = AtThreePositions(null, fallback = MqProgressFallback.Wavy)

/** A stream that cannot be seeked: the played part loses its color. */
@PreviewTest
@MqPreviews
@Composable
fun WaveformDisabledScreenshot() {
    MediatheqTheme {
        Surface {
            MqWaveformProgress(
                previewLevels,
                0.4f,
                "",
                onSeek = {},
                Modifier.padding(MediatheqSpacing.l),
                enabled = false,
            )
        }
    }
}
