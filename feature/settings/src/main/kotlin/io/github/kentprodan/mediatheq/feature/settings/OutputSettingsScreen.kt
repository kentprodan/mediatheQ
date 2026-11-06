package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTabButtons
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.ui.formatFrequency
import kotlin.math.roundToInt

@Composable
internal fun OutputListRoute(
    onOpen: (OutputId) -> Unit,
    onBack: () -> Unit,
    viewModel: OutputSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OutputListScreen(state, onOpen, viewModel::onAction, onBack)
}

@Composable
internal fun OutputRoute(
    id: OutputId?,
    onBack: () -> Unit,
    viewModel: OutputSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OutputScreen(state, id, viewModel::onAction, onBack)
}

/** Settings -> Playback -> Output settings (#140): every remembered output, the current one marked; delete forgets. */
@Composable
fun OutputListScreen(
    state: OutputSettingsUiState,
    onOpen: (OutputId) -> Unit,
    onAction: (OutputSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    // The output asked about, by its id's text (saveable).
    var forgetting by rememberSaveable { mutableStateOf<String?>(null) }
    val content = state as? OutputSettingsUiState.Content
    SettingsScaffold(stringResource(R.string.settings_outputs), onBack) {
        content ?: return@SettingsScaffold
        item(key = "intro") {
            Text(
                stringResource(R.string.settings_outputs_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "outputs") {
            Group(R.string.settings_outputs_devices) {
                content.outputs.forEach { output ->
                    item { shapes ->
                        OutputRow(shapes, output, output.id == content.current, { onOpen(output.id) }) {
                            forgetting = output.id.toString()
                        }
                    }
                }
            }
        }
    }
    val asked = forgetting?.let { id -> content?.outputs?.firstOrNull { it.id.toString() == id } }
    if (asked != null) {
        ForgetDialog(asked.displayName(), { onAction(OutputSettingsAction.Forget(asked.id)) }) { forgetting = null }
    }
}

@Composable
private fun OutputRow(
    shapes: ListItemShapes,
    output: RememberedOutput,
    current: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    val name = output.displayName()
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = settingsColors(),
        leadingContent = { Icon(output.id.kind.icon, contentDescription = null) },
        supportingContent =
            if (current) {
                { Text(stringResource(R.string.settings_output_current), color = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        trailingContent =
            if (output.id == OutputId.Phone) {
                null
            } else {
                {
                    IconButton(onClick = onForget) {
                        Icon(MqIcons.Delete, contentDescription = stringResource(R.string.settings_output_forget, name))
                    }
                }
            },
    ) { Text(name) }
}

/** Forgetting removes the output's settings; it asks first. */
@Composable
private fun ForgetDialog(
    name: String,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = null,
        confirmLabel = stringResource(R.string.settings_output_forget_confirm),
        onConfirm = {
            onForget()
            onDismiss()
        },
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { Text(stringResource(R.string.settings_output_forget_message, name)) }
}

/**
 * One output's page (#140): tabs Settings (normalization, Bypass processing, bit-perfect for USB, Restore defaults) and
 * Equalizer. [id] `null` = the current output (Now Playing's Equalizer button opens it on the Equalizer tab).
 */
@Composable
fun OutputScreen(
    state: OutputSettingsUiState,
    id: OutputId?,
    onAction: (OutputSettingsAction) -> Unit,
    onBack: () -> Unit,
    initialTab: Int = if (id == null) EQUALIZER_TAB else SETTINGS_TAB,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var restoring by rememberSaveable { mutableStateOf(false) }
    val content = state as? OutputSettingsUiState.Content
    val output = content?.output(id)
    val tabs =
        listOf(stringResource(R.string.settings_output_tab_settings), stringResource(R.string.settings_audio_equalizer))
    SettingsScaffold(output?.displayName().orEmpty(), onBack) {
        if (content == null || output == null) return@SettingsScaffold
        // What the output does now is only known for the one playing.
        val info =
            if (output.id == content.current) {
                content.info
            } else {
                AudioOutputInfo(bitPerfectSupported = content.info.bitPerfectSupported)
            }
        item(key = "tabs") { MqTabButtons(tabs, tab, { tab = it }, Modifier.fillMaxWidth()) }
        val set = { profile: OutputProfile -> onAction(OutputSettingsAction.SetProfile(output.id, profile)) }
        if (tab == SETTINGS_TAB) {
            outputSettingsGroups(output, info, set) { restoring = true }
        } else {
            equalizerGroups(output, info, set) { index, gain ->
                onAction(OutputSettingsAction.SetEqBand(output.id, index, gain))
            }
        }
    }
    if (restoring && output != null) {
        RestoreDefaultsDialog({ onAction(OutputSettingsAction.SetProfile(output.id, null)) }) { restoring = false }
    }
}

private fun LazyListScope.outputSettingsGroups(
    output: RememberedOutput,
    info: AudioOutputInfo,
    set: (OutputProfile) -> Unit,
    onRestore: () -> Unit,
) {
    val profile = output.profile
    item(key = "processing") {
        Group(R.string.settings_output_processing) {
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_normalization),
                    if (profile.bypass) {
                        stringResource(R.string.settings_output_bypassed)
                    } else {
                        stringResource(R.string.settings_normalization_summary)
                    },
                    Normalization.entries,
                    selected = profile.normalization,
                    label = { stringResource(it.label) },
                    onSelect = { set(profile.copy(normalization = it)) },
                    enabled = !profile.bypass,
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_output_bypass),
                    stringResource(R.string.settings_output_bypass_summary),
                    profile.bypass,
                ) { set(profile.copy(bypass = it)) }
            }
            // The phone's value only seeds new DACs (#140, ADR 0032): it is not shown there.
            if (output.id.kind == OutputKind.Usb) {
                item { shapes ->
                    SwitchItem(
                        shapes,
                        stringResource(R.string.settings_audio_bit_perfect),
                        bitPerfectSummary(info),
                        profile.bitPerfectUsb && info.bitPerfectSupported,
                        enabled = info.bitPerfectSupported,
                    ) { set(profile.copy(bitPerfectUsb = it)) }
                }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

@Composable
private fun bitPerfectSummary(info: AudioOutputInfo): String {
    val rate = info.bitPerfectSampleRateHz
    return when {
        !info.bitPerfectSupported -> stringResource(R.string.settings_audio_bit_perfect_unsupported)
        rate != null -> stringResource(R.string.settings_audio_bit_perfect_active, formatFrequency(rate))
        info.bitPerfectNoMatch -> stringResource(R.string.settings_audio_bit_perfect_no_match)
        else -> stringResource(R.string.settings_audio_bit_perfect_summary)
    }
}

private fun LazyListScope.equalizerGroups(
    output: RememberedOutput,
    info: AudioOutputInfo,
    set: (OutputProfile) -> Unit,
    onBand: (Int, Float) -> Unit,
) {
    val profile = output.profile
    item(key = "equalizer") {
        // Disabled controls always say why (media3 skill, section 7).
        val reason =
            when {
                profile.bypass -> stringResource(R.string.settings_output_bypassed)
                info.equalizerBlocked -> stringResource(R.string.settings_audio_equalizer_blocked)
                else -> null
            }
        val adjustable = profile.equalizerOn && reason == null
        Group(R.string.settings_audio_equalizer) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_audio_equalizer),
                    reason ?: stringResource(R.string.settings_audio_equalizer_summary),
                    profile.equalizerOn,
                    enabled = reason == null,
                ) { set(profile.copy(equalizerOn = it)) }
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_eq_preset),
                    null,
                    EqPreset.entries,
                    selected = profile.eqPreset,
                    label = { stringResource(it.label) },
                    onSelect = { set(profile.copy(eqPreset = it)) },
                    enabled = adjustable,
                )
            }
            item { shapes -> BandsItem(shapes, profile.eqGainsDb, adjustable, onBand) }
        }
    }
}

@Composable
private fun BandsItem(
    shapes: ListItemShapes,
    gains: List<Float>,
    enabled: Boolean,
    onBand: (Int, Float) -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = {
            Column {
                AudioPreferences.EQ_BANDS_HZ.forEachIndexed { index, hz ->
                    BandSlider(formatFrequency(hz), gains[index], enabled) { onBand(index, it) }
                }
            }
        },
    ) { Text(stringResource(R.string.settings_eq_bands)) }
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
    Column(Modifier.fillMaxWidth()) {
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
                Modifier.fillMaxWidth().padding(top = MediatheqSpacing.xs).semantics {
                    contentDescription = label
                    stateDescription = gainLabel
                },
        )
    }
}

@Composable
private fun RememberedOutput.displayName(): String =
    if (id == OutputId.Phone) stringResource(R.string.settings_output_phone) else name

private val OutputKind.icon: ImageVector
    @Composable get() =
        when (this) {
            OutputKind.Phone -> MqIcons.Smartphone
            OutputKind.Bluetooth -> MqIcons.Headphones
            OutputKind.Usb -> MqIcons.Usb
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

internal const val SETTINGS_TAB = 0
internal const val EQUALIZER_TAB = 1
