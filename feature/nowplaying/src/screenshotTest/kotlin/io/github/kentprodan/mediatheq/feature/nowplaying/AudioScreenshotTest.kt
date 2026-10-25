package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo

@Composable
private fun Screen(state: AudioUiState) {
    MediatheqTheme { AudioScreen(state, onAction = {}, onClose = {}) }
}

/** Defaults: bit-perfect off, equalizer off (presets and bands disabled). */
@PreviewTest
@MqPreviews
@Composable
fun AudioDefaultScreenshot() {
    Screen(AudioUiState())
}

/** Equalizer on with custom bands. */
@PreviewTest
@MqPreviews
@Composable
fun AudioEqualizerOnScreenshot() {
    Screen(
        AudioUiState(
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
fun AudioBitPerfectScreenshot() {
    Screen(
        AudioUiState(
            AudioPreferences(bitPerfectUsb = true, equalizerOn = true, eqPreset = EqPreset.BassBoost),
            AudioOutputInfo(bitPerfectSampleRateHz = 44_100, usbDacConnected = true),
        ),
    )
}

/** Below Android 14: bit-perfect cannot be turned on. */
@PreviewTest
@MqPreviews
@Composable
fun AudioUnsupportedScreenshot() {
    Screen(AudioUiState(output = AudioOutputInfo(bitPerfectSupported = false)))
}
