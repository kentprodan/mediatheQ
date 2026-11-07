package io.github.kentprodan.mediatheq.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAudioOutputRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class OutputSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val outputs = FakeAudioOutputRepository()
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { OutputSettingsViewModel(outputs, playback) }
    private val headphones = OutputId(OutputKind.Bluetooth, "m4")

    private fun content() = viewModel.uiState.value as OutputSettingsUiState.Content

    @Test
    fun `nothing used yet shows the phone as the current output`() =
        runTest {
            viewModel.uiState.test {
                assertThat(expectMostRecentItem())
                    .isEqualTo(
                        OutputSettingsUiState.Content(
                            listOf(RememberedOutput(OutputId.Phone, "", OutputProfile())),
                            OutputId.Phone,
                        ),
                    )
            }
        }

    @Test
    fun `outputs, the current one and what it does now show`() =
        runTest {
            outputs.use(headphones, "MOMENTUM 4")
            playback.state.value = PlayerUiModel(output = AudioOutputInfo(usbDacConnected = true))

            viewModel.uiState.test {
                val content = expectMostRecentItem() as OutputSettingsUiState.Content
                assertThat(content.outputs.map { it.id }).containsExactly(OutputId.Phone, headphones).inOrder()
                assertThat(content.current).isEqualTo(headphones)
                assertThat(content.output(null)?.name).isEqualTo("MOMENTUM 4")
                assertThat(content.info).isEqualTo(AudioOutputInfo(usbDacConnected = true))
            }
        }

    @Test
    fun `a profile change, a band, a restore and forgetting reach the outputs`() =
        runTest {
            outputs.use(headphones, "MOMENTUM 4")
            viewModel.uiState.test {
                expectMostRecentItem()

                viewModel.onAction(OutputSettingsAction.SetProfile(headphones, OutputProfile(bypass = true)))
                assertThat(content().output(headphones)?.profile).isEqualTo(OutputProfile(bypass = true))

                viewModel.onAction(OutputSettingsAction.SetEqBand(headphones, 1, 3f))
                assertThat(content().output(headphones)?.profile?.eqPreset).isEqualTo(EqPreset.Custom)

                viewModel.onAction(OutputSettingsAction.SetProfile(headphones, null))
                assertThat(content().output(headphones)?.profile).isEqualTo(OutputProfile())

                viewModel.onAction(OutputSettingsAction.Forget(headphones))
                assertThat(content().outputs.map { it.id }).containsExactly(OutputId.Phone)
                assertThat(content().current).isEqualTo(OutputId.Phone)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
