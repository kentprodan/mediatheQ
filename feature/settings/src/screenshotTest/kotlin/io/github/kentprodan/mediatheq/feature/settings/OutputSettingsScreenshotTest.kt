package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo

private val headphones = OutputId(OutputKind.Bluetooth, "m4")
private val car = OutputId(OutputKind.Bluetooth, "vw")
private val dac = OutputId(OutputKind.Usb, "dac")

private fun outputs(
    headphonesProfile: OutputProfile = OutputProfile(normalization = Normalization.Track),
    current: OutputId = headphones,
    info: AudioOutputInfo = AudioOutputInfo(),
) = OutputSettingsUiState.Content(
    listOf(
        RememberedOutput(OutputId.Phone, "", OutputProfile(equalizerOn = true, eqPreset = EqPreset.BassBoost)),
        RememberedOutput(dac, "Apple USB-C to 3.5mm", OutputProfile(bitPerfectUsb = true)),
        RememberedOutput(headphones, "MOMENTUM 4", headphonesProfile),
        RememberedOutput(car, "VW", OutputProfile(bypass = true)),
    ),
    current,
    info,
)

@Composable
private fun Output(
    state: OutputSettingsUiState,
    id: OutputId,
    tab: Int = SETTINGS_TAB,
) = MediatheqTheme { OutputScreen(state, id, {}, {}, tab) }

/** Output settings (#140): the phone, a DAC and two Bluetooth devices, the headphones current. */
@PreviewTest
@MqPreviews
@Composable
fun OutputListScreenshot() {
    MediatheqTheme { OutputListScreen(outputs(), {}, {}, {}) }
}

/** An output's Settings tab: normalization, Bypass processing, Restore defaults. */
@PreviewTest
@MqPreviews
@Composable
fun OutputSettingsTabScreenshot() = Output(outputs(), headphones)

/** The car bypasses processing: normalization is off and says why. */
@PreviewTest
@MqPreviews
@Composable
fun OutputSettingsBypassScreenshot() = Output(outputs(), car)

/** An output's Equalizer tab with a custom curve. */
@PreviewTest
@MqPreviews
@Composable
fun OutputEqualizerTabScreenshot() =
    Output(
        outputs(
            OutputProfile(equalizerOn = true, eqPreset = EqPreset.Custom, customGainsDb = listOf(4f, 2f, 0f, -3f, 6f)),
        ),
        headphones,
        EQUALIZER_TAB,
    )

/** The DAC playing bit-perfect: its switch says at what rate. */
@PreviewTest
@MqPreviews
@Composable
fun OutputDacBitPerfectScreenshot() =
    Output(outputs(current = dac, info = AudioOutputInfo(bitPerfectSampleRateHz = 96_000, usbDacConnected = true)), dac)
