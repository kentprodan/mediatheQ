package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTimePickerDialog
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.playback.SleepTimerChoice
import io.github.kentprodan.mediatheq.core.playback.SleepTimerState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The track counts and minute lengths offered (#378). */
internal val SLEEP_TRACKS = listOf(1, 2, 3, 5, 10)
internal val SLEEP_MINUTES = listOf(5, 10, 15, 20, 30, 45, 60)

/** The sleep timer's sheet (#378): a ticking [SleepTimerContent], and the time picker it opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    timer: SleepTimerState?,
    last: SleepTimerChoice?,
    onAction: (NowPlayingAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(1.seconds)
            value = Clock.System.now()
        }
    }
    val start = { choice: SleepTimerChoice ->
        onAction(NowPlayingAction.StartSleepTimer(choice))
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SleepTimerContent(
            timer,
            last,
            now,
            onStart = start,
            onCancel = {
                onAction(NowPlayingAction.CancelSleepTimer)
                onDismiss()
            },
            onPickTime = { picking = true },
        )
    }
    if (picking) {
        val initial = (timer?.choice ?: last) as? SleepTimerChoice.At ?: SleepTimerChoice.At(23, 0)
        MqTimePickerDialog(
            title = stringResource(R.string.nowplaying_sleep_stop_at_title),
            initialHour = initial.hour,
            initialMinute = initial.minute,
            confirmLabel = stringResource(R.string.nowplaying_sleep_start),
            dismissLabel = stringResource(R.string.nowplaying_sleep_cancel),
            onConfirm = { hour, minute ->
                picking = false
                start(SleepTimerChoice.At(hour, minute))
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The sleep timer's content: what a running timer waits for and Turn off, else Start again with the last choice;
 * then the track counts and minutes as chips (the running one checked) and Pick a time. [now] ticks the countdown.
 */
@Composable
internal fun SleepTimerContent(
    timer: SleepTimerState?,
    last: SleepTimerChoice?,
    now: Instant,
    onStart: (SleepTimerChoice) -> Unit,
    onCancel: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MediatheqSpacing.l)
            .padding(bottom = MediatheqSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        Text(
            stringResource(R.string.nowplaying_sleep_timer),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (timer != null) {
            Text(
                timerStatus(timer, now),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            OutlinedButton(onCancel, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nowplaying_sleep_turn_off))
            }
        } else if (last != null) {
            FilledTonalButton({ onStart(last) }, Modifier.fillMaxWidth()) {
                Icon(MqIcons.Bedtime, contentDescription = null)
                Text(
                    stringResource(R.string.nowplaying_sleep_again, choiceLabel(last)),
                    Modifier.padding(start = MediatheqSpacing.s),
                )
            }
        }
        SectionTitle(stringResource(R.string.nowplaying_sleep_after_tracks))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
            SLEEP_TRACKS.forEach { count ->
                val choice = SleepTimerChoice.Tracks(count)
                ChoiceChip(choiceLabel(choice), timer?.choice == choice) { onStart(choice) }
            }
        }
        SectionTitle(stringResource(R.string.nowplaying_sleep_after_minutes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
            SLEEP_MINUTES.forEach { minutes ->
                val choice = SleepTimerChoice.Minutes(minutes)
                ChoiceChip(choiceLabel(choice), timer?.choice == choice) { onStart(choice) }
            }
        }
        SectionTitle(stringResource(R.string.nowplaying_sleep_at_time))
        FilledTonalButton(onPickTime, Modifier.fillMaxWidth()) {
            Text(
                (timer?.choice as? SleepTimerChoice.At)?.let { choiceLabel(it) }
                    ?: stringResource(R.string.nowplaying_sleep_pick_time),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
    )
}

/** "This track", "3 tracks", "30 min" or "At 23:15". */
@Composable
internal fun choiceLabel(choice: SleepTimerChoice): String =
    when (choice) {
        is SleepTimerChoice.Tracks -> {
            if (choice.count == 1) {
                stringResource(R.string.nowplaying_sleep_this_track)
            } else {
                pluralStringResource(R.plurals.nowplaying_sleep_tracks, choice.count, choice.count)
            }
        }

        is SleepTimerChoice.Minutes -> {
            stringResource(R.string.nowplaying_sleep_minutes, choice.minutes)
        }

        is SleepTimerChoice.At -> {
            stringResource(R.string.nowplaying_sleep_at, clockTime(choice))
        }
    }

/** What a running [timer] waits for, the countdown measured from [now]. */
@Composable
private fun timerStatus(
    timer: SleepTimerState,
    now: Instant,
): String {
    val left = timer.tracksLeft
    val endsAt = timer.endsAt
    val at = timer.choice as? SleepTimerChoice.At
    return when {
        left == 1 -> {
            stringResource(R.string.nowplaying_sleep_status_end_of_track)
        }

        left != null -> {
            pluralStringResource(R.plurals.nowplaying_sleep_status_tracks, left, left)
        }

        at != null -> {
            stringResource(R.string.nowplaying_sleep_status_at, clockTime(at))
        }

        endsAt != null -> {
            stringResource(R.string.nowplaying_sleep_status_in, formatDuration((endsAt - now).coerceAtLeast(0.seconds)))
        }

        else -> {
            ""
        }
    }
}

/** 23:15 or 11:15 PM, as the phone's locale writes times. */
@Composable
private fun clockTime(at: SleepTimerChoice.At): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return LocalTime
        .of(
            at.hour,
            at.minute,
        ).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}
