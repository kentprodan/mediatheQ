package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class BackupScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<BackupAction>()
    private val opened = mutableListOf<String>()

    private fun show(state: BackupUiState = BackupUiState()) =
        composeRule.setContent {
            MediatheqTheme {
                BackupScreen(
                    state,
                    { actions += it },
                    onCreate = { opened += "create" },
                    onPick = { opened += "pick" },
                ) {}
            }
        }

    @Test
    fun `the rows open the file pickers, under the note about passwords`() {
        show()

        composeRule.onNodeWithText("never passwords", substring = true).assertExists()
        composeRule.onNodeWithText("Create a backup").performClick()
        composeRule.onNodeWithText("Pick a backup to restore").performClick()

        assertThat(opened).containsExactly("create", "pick").inOrder()
    }

    @Test
    fun `a restore with servers to sign in to says so, and goes on OK`() {
        show(BackupUiState(message = BackupMessage.Restored(2)))

        composeRule.onNodeWithText("Sign in to 2 servers", substring = true).assertExists()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(actions).containsExactly(BackupAction.DismissMessage)
    }

    @Test
    fun `a file that is no backup says nothing changed`() {
        show(BackupUiState(message = BackupMessage.Invalid))

        composeRule.onNodeWithText("not a mediatheQ backup", substring = true).assertExists()
    }
}
