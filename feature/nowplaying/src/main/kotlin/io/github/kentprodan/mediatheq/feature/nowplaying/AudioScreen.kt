package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.formatFrequency
import kotlin.math.roundToInt

/**
 * Audio settings, opened from Now Playing until the Settings screen exists (#25): bit-perfect USB and the equalizer
 * (presets and five bands). The equalizer is unavailable while bit-perfect plays, and the screen says why.
 */
@Composable
fun AudioScreen(
    state: AudioUiState,
    onAction: (NowPlayingAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.nowplaying_audio)
    // A Surface, not a background: the band labels need its content color too.
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().semantics { paneTitle = title }) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.nowplaying_audio_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(bottom = MediatheqSpacing.l),
            ) {
                BitPerfectItem(state, onAction)
                EqualizerSection(state, onAction)
            }
        }
    }
}

@Composable
private fun BitPerfectItem(
    state: AudioUiState,
    onAction: (NowPlayingAction) -> Unit,
) {
    val output = state.output
    val rate = output.bitPerfectSampleRateHz
    SwitchItem(
        title = stringResource(R.string.nowplaying_audio_bit_perfect),
        supporting =
            when {
                !output.bitPerfectSupported -> {
                    stringResource(R.string.nowplaying_audio_bit_perfect_unsupported)
                }

                rate != null -> {
                    stringResource(R.string.nowplaying_audio_bit_perfect_active, formatFrequency(rate))
                }

                output.usbDacConnected && state.preferences.bitPerfectUsb -> {
                    stringResource(R.string.nowplaying_audio_bit_perfect_no_match)
                }

                else -> {
                    stringResource(R.string.nowplaying_audio_bit_perfect_summary)
                }
            },
        checked = state.preferences.bitPerfectUsb && output.bitPerfectSupported,
        enabled = output.bitPerfectSupported,
        onCheckedChange = { onAction(NowPlayingAction.SetBitPerfectUsb(it)) },
    )
}

@Composable
private fun EqualizerSection(
    state: AudioUiState,
    onAction: (NowPlayingAction) -> Unit,
) {
    val blocked = state.output.equalizerBlocked
    val preferences = state.preferences
    val adjustable = preferences.equalizerOn && !blocked
    SwitchItem(
        title = stringResource(R.string.nowplaying_audio_equalizer),
        supporting =
            if (blocked) {
                stringResource(R.string.nowplaying_audio_equalizer_blocked)
            } else {
                stringResource(R.string.nowplaying_audio_equalizer_summary)
            },
        checked = preferences.equalizerOn,
        enabled = !blocked,
        onCheckedChange = { onAction(NowPlayingAction.SetEqualizerOn(it)) },
    )
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
    ) {
        EqPreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == preferences.eqPreset,
                onClick = { onAction(NowPlayingAction.SetEqPreset(preset)) },
                label = { Text(stringResource(preset.label)) },
                enabled = adjustable,
            )
        }
    }
    AudioPreferences.EQ_BANDS_HZ.forEachIndexed { index, hz ->
        BandSlider(
            label = formatFrequency(hz),
            gainDb = preferences.eqGainsDb[index],
            enabled = adjustable,
            onGain = { onAction(NowPlayingAction.SetEqBand(index, it)) },
        )
    }
}

@Composable
private fun SwitchItem(
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, modifier = Modifier.semantics { heading() }) },
        supportingContent = { Text(supporting) },
        // The whole row toggles, so TalkBack reads title, summary and state as one control.
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/** One band: its frequency, the gain, and a slider in 1 dB steps that sets the gain once on release. */
@Composable
private fun BandSlider(
    label: String,
    gainDb: Float,
    enabled: Boolean,
    onGain: (Float) -> Unit,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: gainDb
    val gainLabel = stringResource(R.string.nowplaying_audio_gain_db, shown.roundToInt())
    Column(Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(gainLabel, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = shown,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let(onGain)
                dragged = null
            },
            valueRange = AudioPreferences.EQ_GAIN_DB,
            steps = (AudioPreferences.EQ_GAIN_DB.endInclusive - AudioPreferences.EQ_GAIN_DB.start).toInt() - 1,
            enabled = enabled,
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = label
                    stateDescription = gainLabel
                },
        )
    }
}

private val EqPreset.label: Int
    get() =
        when (this) {
            EqPreset.Flat -> R.string.nowplaying_eq_flat
            EqPreset.BassBoost -> R.string.nowplaying_eq_bass
            EqPreset.TrebleBoost -> R.string.nowplaying_eq_treble
            EqPreset.Vocal -> R.string.nowplaying_eq_vocal
            EqPreset.Custom -> R.string.nowplaying_eq_custom
        }
