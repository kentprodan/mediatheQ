package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ProvidersScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val home = Account("mediatheq.db", "Home", "Ana", "http://nas:8096", active = true)
    private val office = Account("mediatheq-2.db", "Office", "Bo", "https://office.org", active = false)
    private val actions = mutableListOf<ProvidersAction>()
    private val events = mutableListOf<String>()

    private fun show(state: ProvidersUiState = ProvidersUiState.Content(listOf(home, office))) {
        composeRule.setContent {
            MediatheqTheme {
                ProvidersScreen(
                    state,
                    { actions += it },
                    onAdd = { events += "add" },
                    onSignInAgain = { events += "again ${it.id}" },
                    onBack = { events += "back" },
                )
            }
        }
    }

    @Test
    fun `each account shows its name, user and address, the active one marked`() {
        show()

        composeRule.onNodeWithText("Ana · http://nas:8096").assertExists()
        composeRule.onNodeWithText("Bo · https://office.org").assertExists()
        composeRule.onAllNodesWithText("Active").assertCountEquals(1)
    }

    @Test
    fun `a signed-out account says so and offers only Sign in and remove`() {
        show(ProvidersUiState.Content(listOf(home, office.copy(signedIn = false))))

        composeRule.onNodeWithText("Signed out").assertExists()
        composeRule.onNodeWithContentDescription("More options for Office").performClick()
        composeRule.onNodeWithText("Make active").assertDoesNotExist()
        composeRule.onNodeWithText("Edit address").assertDoesNotExist()
        composeRule.onNodeWithText("Sign out and remove").assertExists()
        composeRule.onNodeWithText("Sign in").performClick()

        assertThat(events).containsExactly("again ${office.id}")
    }

    @Test
    fun `make active asks first, then switches`() {
        show()

        composeRule.onNodeWithContentDescription("More options for Office").performClick()
        composeRule.onNodeWithText("Make active").performClick()
        composeRule.onNodeWithText("Switch to Office?").assertExists()
        composeRule.onNodeWithText("Switch").performClick()

        assertThat(actions).containsExactly(ProvidersAction.MakeActive(office.id))
    }

    @Test
    fun `the active account's menu has sync now and no make active`() {
        show()

        composeRule.onNodeWithContentDescription("More options for Home").performClick()

        composeRule.onNodeWithText("Make active").assertDoesNotExist()
        composeRule.onNodeWithText("Sync now").performClick()
        assertThat(actions).containsExactly(ProvidersAction.SyncNow)
    }

    @Test
    fun `removing asks first and deletes the downloads unless unchecked`() {
        show()

        composeRule.onNodeWithContentDescription("More options for Office").performClick()
        composeRule.onNodeWithText("Sign out and remove").performClick()
        composeRule.onNodeWithText("Also delete its downloads").performClick()
        composeRule.onNodeWithText("Remove").performClick()

        assertThat(actions).containsExactly(ProvidersAction.Remove(office.id, deleteDownloads = false))
    }

    @Test
    fun `sign in again and edit address reach their callbacks`() {
        show()

        composeRule.onNodeWithContentDescription("More options for Office").performClick()
        composeRule.onNodeWithText("Sign in again").performClick()
        composeRule.onNodeWithContentDescription("More options for Office").performClick()
        composeRule.onNodeWithText("Edit address").performClick()

        assertThat(events).containsExactly("again ${office.id}")
        assertThat(actions).containsExactly(ProvidersAction.StartEdit(office.id))
    }

    @Test
    fun `the address field says why the address was not saved`() {
        // The body alone: a text field inside a dialog window never lets Compose idle on Robolectric.
        composeRule.setContent {
            MediatheqTheme {
                EditAddressBody(
                    "http://nas:8096",
                    AddressEdit(home.id, error = AddressError.OtherServer),
                ) {}
            }
        }

        composeRule.onNodeWithText("Another server answers at this address").assertExists()
        composeRule.onNodeWithText("http://nas:8096").assertExists()
    }

    @Test
    fun `add opens the provider picker, Jellyfin goes on to sign in`() {
        show()

        composeRule.onNodeWithText("Add media provider").performClick()
        composeRule.onNodeWithText("Jellyfin").performClick()

        assertThat(events).containsExactly("add")
    }

    @Test
    fun `the first row moves down for TalkBack`() {
        show()

        val first = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))[0]
        val labels = first.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(labels.map { it.label }).containsExactly("Move down")
        labels.single().action()

        assertThat(actions).containsExactly(ProvidersAction.Reorder(listOf(office.id, home.id)))
    }
}
