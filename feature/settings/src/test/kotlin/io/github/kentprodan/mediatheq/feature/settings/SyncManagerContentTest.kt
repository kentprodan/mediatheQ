package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.SyncRecord
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class SyncManagerContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SyncManagerAction>()
    private val finished = Instant.fromEpochSeconds(1_000_000)
    private val card =
        SyncCard(
            "mediatheq.db",
            "Home",
            "Ana",
            "http://nas:8096",
            active = true,
            connection = Connection.Online,
            last = SyncRecord(finished, null, 3120, 24.seconds),
            running = null,
        )

    private fun show(state: SyncManagerUiState) =
        composeRule.setContent {
            MediatheqTheme {
                SyncManagerContent(state, finished + 5.minutes, onAction = {
                    actions +=
                        it
                })
            }
        }

    @Test
    fun `the last sync shows its time, count and duration, and Sync all syncs`() {
        show(SyncManagerUiState.Content(listOf(card), null, false))

        composeRule.onNodeWithText("Successful").assertIsDisplayed()
        composeRule.onNodeWithText("5 minutes ago · 3120 tracks in 24 seconds").assertIsDisplayed()
        composeRule.onNodeWithText("Sync all").assertIsEnabled().performClick()

        assertThat(actions).containsExactly(SyncManagerAction.SyncAll)
    }

    @Test
    fun `offline, Sync all is disabled and says why`() {
        show(
            SyncManagerUiState.Content(
                listOf(card.copy(connection = Connection.Offline)),
                OfflineReason.OfflineMode,
                false,
            ),
        )

        composeRule.onNodeWithText("Sync all").assertIsNotEnabled()
        composeRule.onNodeWithText("Offline mode is on: turn it off to sync.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Check Home again").assertIsNotEnabled()
    }

    @Test
    fun `a failure names its error`() {
        val failed = card.copy(last = SyncRecord(Instant.fromEpochSeconds(0), MqError.Server(503), 0, 1.seconds))
        show(SyncManagerUiState.Content(listOf(failed), null, false))

        composeRule.onNodeWithText("Failed: server error 503").assertIsDisplayed()
    }

    @Test
    fun `Retry asks that account's server again`() {
        show(SyncManagerUiState.Content(listOf(card), null, false))

        composeRule.onNodeWithContentDescription("Check Home again").performClick()

        assertThat(actions).containsExactly(SyncManagerAction.Retry("mediatheq.db"))
    }
}
