package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.playback.SleepTimerChoice
import io.github.kentprodan.mediatheq.core.playback.SleepTimerState
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val now = Instant.parse("2026-11-16T22:00:00Z")

@Composable
private fun Sheet(
    timer: SleepTimerState?,
    last: SleepTimerChoice?,
) = SleepTimerContent(timer, last, now, {}, {}, {})

/** The sleep timer sheet's content (#378), on the sheet's color (popups render blank): off with a last choice. */
@PreviewTest
@MqPreviews
@Composable
fun SleepTimerOffScreenshot() =
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Sheet(null, SleepTimerChoice.Minutes(30))
        }
    }

/** Running: a countdown with its chip checked, and the end of this track. */
@PreviewTest
@MqPreviews
@Composable
fun SleepTimerRunningScreenshot() =
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l)) {
                Sheet(
                    SleepTimerState(SleepTimerChoice.Minutes(30), endsAt = now + 23.minutes + 10.seconds),
                    SleepTimerChoice.Minutes(30),
                )
                HorizontalDivider()
                Sheet(SleepTimerState(SleepTimerChoice.Tracks(1), tracksLeft = 1), null)
            }
        }
    }
