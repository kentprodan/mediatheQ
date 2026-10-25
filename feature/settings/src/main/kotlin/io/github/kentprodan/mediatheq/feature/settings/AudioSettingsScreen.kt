package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.formatFrequency
import kotlin.math.roundToInt

@Composable
internal fun AudioSettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AudioSettingsScreen(state, viewModel::onAction, onBack)
}

/**
 * Settings -> Audio: bit-perfect USB and the equalizer (presets and five bands). The equalizer is unavailable while
 * bit-perfect plays, and the screen says why.
 */
@Composable
fun AudioSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_audio)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.settings_audio_close))
                    }
                },
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val content = state as? SettingsUiState.Content ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = MediatheqSpacing.l),
        ) {
            BitPerfectItem(content, onAction)
            EqualizerSection(content, onAction)
        }
    }
}

@Composable
private fun BitPerfectItem(
    state: SettingsUiState.Content,
    onAction: (SettingsAction) -> Unit,
) {
    val output = state.output
    val rate = output.bitPerfectSampleRateHz
    SwitchItem(
        title = stringResource(R.string.settings_audio_bit_perfect),
        supporting =
            when {
                !output.bitPerfectSupported -> {
                    stringResource(R.string.settings_audio_bit_perfect_unsupported)
                }

                rate != null -> {
                    stringResource(R.string.settings_audio_bit_perfect_active, formatFrequency(rate))
                }

                output.bitPerfectNoMatch -> {
                    stringResource(R.string.settings_audio_bit_perfect_no_match)
                }

                else -> {
                    stringResource(R.string.settings_audio_bit_perfect_summary)
                }
            },
        checked = state.audio.bitPerfectUsb && output.bitPerfectSupported,
        enabled = output.bitPerfectSupported,
        onCheckedChange = { onAction(SettingsAction.SetBitPerfectUsb(it)) },
    )
}

@Composable
private fun EqualizerSection(
    state: SettingsUiState.Content,
    onAction: (SettingsAction) -> Unit,
) {
    val blocked = state.output.equalizerBlocked
    val preferences = state.audio
    val adjustable = preferences.equalizerOn && !blocked
    SwitchItem(
        title = stringResource(R.string.settings_audio_equalizer),
        supporting =
            if (blocked) {
                stringResource(R.string.settings_audio_equalizer_blocked)
            } else {
                stringResource(R.string.settings_audio_equalizer_summary)
            },
        checked = preferences.equalizerOn,
        enabled = !blocked,
        onCheckedChange = { onAction(SettingsAction.SetEqualizerOn(it)) },
    )
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
    ) {
        EqPreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == preferences.eqPreset,
                onClick = { onAction(SettingsAction.SetEqPreset(preset)) },
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
            onGain = { onAction(SettingsAction.SetEqBand(index, it)) },
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
    val gainLabel = stringResource(R.string.settings_audio_gain_db, shown.roundToInt())
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
            EqPreset.Flat -> R.string.settings_eq_flat
            EqPreset.BassBoost -> R.string.settings_eq_bass
            EqPreset.TrebleBoost -> R.string.settings_eq_treble
            EqPreset.Vocal -> R.string.settings_eq_vocal
            EqPreset.Custom -> R.string.settings_eq_custom
        }
