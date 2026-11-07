package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class OutputSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val headphones = OutputId(OutputKind.Bluetooth, "m4")
    private val dac = OutputId(OutputKind.Usb, "dac")
    private val actions = mutableListOf<OutputSettingsAction>()
    private val opened = mutableListOf<OutputId>()

    private fun state(profile: OutputProfile = OutputProfile()) =
        OutputSettingsUiState.Content(
            listOf(
                RememberedOutput(OutputId.Phone, "", OutputProfile()),
                RememberedOutput(dac, "Apple USB-C", profile),
                RememberedOutput(headphones, "MOMENTUM 4", profile),
            ),
            current = headphones,
        )

    private fun showList() =
        composeRule.setContent {
            MediatheqTheme { OutputListScreen(state(), { opened += it }, { actions += it }, onBack = {}) }
        }

    private fun showOutput(
        id: OutputId?,
        profile: OutputProfile = OutputProfile(),
    ) = composeRule.setContent {
        MediatheqTheme { OutputScreen(state(profile), id, { actions += it }, onBack = {}) }
    }

    @Test
    fun `the list marks the current output, opens one and forgets after asking, never the phone`() {
        showList()

        composeRule.onNodeWithText("Current output").assertExists()
        composeRule.onNode(hasText("MOMENTUM 4") and hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("Forget Phone").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Forget Apple USB-C").performClick()
        composeRule.onNodeWithText("Forget").performClick()

        assertThat(opened).containsExactly(headphones)
        assertThat(actions).containsExactly(OutputSettingsAction.Forget(dac))
    }

    @Test
    fun `bypass greys out normalization and the equalizer and says why`() {
        showOutput(headphones, OutputProfile(bypass = true))

        composeRule.onNodeWithText("Track").assertIsNotEnabled()
        composeRule.onNode(hasText("Equalizer") and hasClickAction()).performClick()

        composeRule.onNodeWithText("Off: this output bypasses processing.").assertExists()
    }

    @Test
    fun `a Bluetooth output has no bit-perfect switch`() {
        showOutput(headphones)

        composeRule.onNodeWithText("Bypass processing").assertExists()
        composeRule.onNodeWithText("Bit-perfect USB").assertDoesNotExist()
    }

    @Test
    fun `a USB output offers bit-perfect`() {
        showOutput(dac)

        composeRule.onNodeWithText("Bit-perfect USB").performClick()

        assertThat(changed(dac)).isEqualTo(OutputProfile(bitPerfectUsb = true))
    }

    @Test
    fun `the current output opens on its equalizer, a change applies to it`() {
        showOutput(null)

        composeRule.onNodeWithText("MOMENTUM 4").assertExists()
        composeRule.onNode(hasText("Equalizer") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("Vocal").assertIsNotEnabled()
        composeRule.onNodeWithText("Uses more battery", substring = true).performClick()

        assertThat(changed(headphones)).isEqualTo(OutputProfile(equalizerOn = true))
    }

    @Test
    fun `the current output's page stays on it when the route changes meanwhile`() {
        var state by mutableStateOf(state())
        composeRule.setContent { MediatheqTheme { OutputScreen(state, null, { actions += it }, onBack = {}) } }
        composeRule.onNodeWithText("MOMENTUM 4").assertExists()

        state = state.copy(current = dac)

        composeRule.onNodeWithText("MOMENTUM 4").assertExists()
        composeRule.onNodeWithText("Apple USB-C").assertDoesNotExist()
    }

    /** The one change sent for [id], applied to the default profile. */
    private fun changed(id: OutputId): OutputProfile {
        val update = actions.single() as OutputSettingsAction.UpdateProfile
        assertThat(update.id).isEqualTo(id)
        return update.change(OutputProfile())
    }
}
