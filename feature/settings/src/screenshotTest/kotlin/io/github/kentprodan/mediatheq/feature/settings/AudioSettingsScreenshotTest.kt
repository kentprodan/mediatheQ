package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo

@Composable
private fun Screen(state: SettingsUiState) {
    MediatheqTheme { AudioSettingsScreen(state, onAction = {}, onBack = {}) }
}

private fun audioState(
    audio: AudioPreferences = AudioPreferences(),
    output: AudioOutputInfo = AudioOutputInfo(),
) = SettingsUiState.Content("Home", "Ana", audio, output = output)

/** Defaults: bit-perfect off, equalizer off (presets and bands disabled). */
@PreviewTest
@MqPreviews
@Composable
fun AudioSettingsDefaultScreenshot() {
    Screen(audioState())
}

/** Equalizer on with custom bands. */
@PreviewTest
@MqPreviews
@Composable
fun AudioSettingsEqualizerOnScreenshot() {
    Screen(
        audioState(
            AudioPreferences(
                equalizerOn = true,
                eqPreset = EqPreset.Custom,
                customGainsDb = listOf(6f, 3f, 0f, -2f, 4f),
            ),
        ),
    )
}

/** Bit-perfect playing: the equalizer is unavailable and says why. */
@PreviewTest
@MqPreviews
@Composable
fun AudioSettingsBitPerfectScreenshot() {
    Screen(
        audioState(
            AudioPreferences(bitPerfectUsb = true, equalizerOn = true, eqPreset = EqPreset.BassBoost),
            AudioOutputInfo(bitPerfectSampleRateHz = 44_100, usbDacConnected = true),
        ),
    )
}

/** Below Android 14: bit-perfect cannot be turned on. */
@PreviewTest
@MqPreviews
@Composable
fun AudioSettingsUnsupportedScreenshot() {
    Screen(audioState(output = AudioOutputInfo(bitPerfectSupported = false)))
}
