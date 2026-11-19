package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.AppMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings search (#536): every indexed label shows on its page, and the search finds it there. */
@RunWith(RobolectricTestRunner::class)
// Tall enough that every row of the longest page is composed.
@Config(application = Application::class, qualifiers = "w412dp-h20000dp-mdpi")
class SettingsIndexTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = composeRule.activity.getString(id)

    @Test
    fun `every indexed label shows on its page and the search finds it there`() {
        var page by mutableStateOf(SettingsPage.Interface)
        composeRule.setContent {
            MediatheqTheme {
                // Both modes: the video and the music settings show.
                SettingsPageScreen(page, SettingsUiState.Content(appMode = AppMode.Both), {}, {}, {})
            }
        }
        assertThat(settingsIndex.keys).containsExactlyElementsIn(SettingsPage.entries)
        SettingsPage.entries.forEach { p ->
            page = p
            composeRule.waitForIdle()
            val shown = mutableSetOf<String>()

            fun walk(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { shown += it.text }
                node.children.forEach(::walk)
            }
            walk(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            settingsIndex.getValue(p).map(::text).forEach { label ->
                assertWithMessage("$label on ${p.name}").that(shown).contains(label)
                assertWithMessage(
                    "search for $label",
                ).that(searchSettings(label, ::text)).contains(SettingsHit(label, p))
            }
        }
    }

    @Test
    fun `a page is found by its title, nothing for a blank query`() {
        assertThat(searchSettings("downloads and", ::text))
            .contains(SettingsHit(text(R.string.settings_storage_page), SettingsPage.Storage))
        assertThat(searchSettings("  ", ::text)).isEmpty()
    }

    @Test
    fun `typing in the hub shows the hits and a tap opens the page`() {
        val opened = mutableListOf<SettingsPage>()
        var query by mutableStateOf("")
        composeRule.setContent {
            MediatheqTheme {
                SettingsHubScreen(onOpen = {}, onBack = {}, query = query, onQuery = { query = it }) { opened += it }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("transpar")
        composeRule.onNodeWithText("Bar transparency").performClick()

        assertThat(opened).containsExactly(SettingsPage.Navigation)
        composeRule.onNode(hasText("Interface")).assertDoesNotExist()
    }
}
