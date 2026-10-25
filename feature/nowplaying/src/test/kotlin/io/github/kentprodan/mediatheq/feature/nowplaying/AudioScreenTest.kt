package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AudioScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<NowPlayingAction>()
    private var closed = false

    private fun setContent(state: AudioUiState) {
        composeRule.setContent {
            MediatheqTheme { AudioScreen(state, onAction = { actions += it }, onClose = { closed = true }) }
        }
    }

    private fun switch(title: String) = composeRule.onNode(isToggleable() and hasText(title))

    @Test
    fun `the switches send their actions and the back button closes`() {
        setContent(AudioUiState())

        switch("Bit-perfect USB").performClick()
        switch("Equalizer").performClick()
        composeRule.onNodeWithContentDescription("Close audio settings").performClick()

        assertThat(actions)
            .containsExactly(NowPlayingAction.SetBitPerfectUsb(true), NowPlayingAction.SetEqualizerOn(true))
            .inOrder()
        assertThat(closed).isTrue()
    }

    @Test
    fun `presets and bands are adjustable only while the equalizer is on`() {
        setContent(AudioUiState())

        composeRule.onNodeWithText("Vocal").assertIsNotEnabled()
        composeRule.onNode(hasContentDescription("60 Hz")).assertIsNotEnabled()
    }

    @Test
    fun `a preset is chosen with its chip`() {
        setContent(AudioUiState(AudioPreferences(equalizerOn = true)))

        composeRule.onNodeWithText("Vocal").assertIsEnabled().performClick()

        assertThat(actions).containsExactly(NowPlayingAction.SetEqPreset(EqPreset.Vocal))
    }

    @Test
    fun `while bit-perfect plays, the equalizer is unavailable and the screen says why`() {
        setContent(
            AudioUiState(
                AudioPreferences(bitPerfectUsb = true, equalizerOn = true),
                AudioOutputInfo(bitPerfectSampleRateHz = 96_000, usbDacConnected = true),
            ),
        )

        switch("Equalizer").assertIsNotEnabled()
        composeRule
            .onNodeWithText("Unavailable while playing bit-perfect: the audio must reach the DAC unchanged.")
            .assertExists()
        composeRule.onNodeWithText("Bit-perfect at 96 kHz. Volume is set on the DAC.").assertExists()
        composeRule.onNodeWithText("Vocal").assertIsNotEnabled()
        composeRule.onNode(hasContentDescription("14 kHz")).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a DAC without a matching format says the track plays normally`() {
        setContent(
            AudioUiState(AudioPreferences(bitPerfectUsb = true), AudioOutputInfo(usbDacConnected = true)),
        )

        composeRule
            .onNodeWithText("The DAC does not take this track's format unchanged, so it plays normally.")
            .assertExists()
        switch("Equalizer").assertIsEnabled()
    }

    @Test
    fun `below Android 14 bit-perfect is off and says why`() {
        setContent(AudioUiState(AudioPreferences(bitPerfectUsb = true), AudioOutputInfo(bitPerfectSupported = false)))

        switch("Bit-perfect USB").assertIsNotEnabled().assertIsOff()
        assertThat(composeRule.onAllNodesWithText("Needs Android 14 or newer.").fetchSemanticsNodes()).hasSize(1)
    }
}
