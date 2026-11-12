package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.navigation.AboutSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.AdvancedSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.BackupSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.InterfaceSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.PlaybackSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.ProvidersSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.StorageSettingsKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsHubScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<SettingsHubEntry>()
    private var back = 0

    private fun show() =
        composeRule.setContent {
            MediatheqTheme {
                SettingsHubScreen(onOpen = { opened += it }, onBack = { back++ })
            }
        }

    private fun row(title: String) = hasText(title) and hasClickAction()

    private fun open(title: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(row(title))
        composeRule.onNode(row(title)).performClick()
    }

    @Test
    fun `every entry opens its page, in the hub's order`() {
        show()

        listOf(
            "Interface",
            "Playback",
            "Offline, cache, and download",
            "Advanced",
            "Manage offline files",
            "Manage media providers",
            "Sync manager",
            "Backup & restore",
        ).forEach(::open)
        // "About" is both a group title and its entry: the clickable row is the entry.
        open("About")
        open("Changelog")
        open("Contact support")

        assertThat(opened).containsExactlyElementsIn(SettingsHubEntry.entries).inOrder()
    }

    @Test
    fun `the About group holds About, Changelog and Contact support`() {
        assertThat(hubGroups.last())
            .isEqualTo(
                R.string.settings_about to
                    listOf(SettingsHubEntry.About, SettingsHubEntry.Changelog, SettingsHubEntry.ContactSupport),
            )
    }

    @Test
    fun `the hub shows every entry exactly once`() {
        assertThat(hubGroups.flatMap { it.second }).containsExactlyElementsIn(SettingsHubEntry.entries).inOrder()
    }

    @Test
    fun `each entry opens its own key`() {
        assertThat(SettingsHubEntry.entries.map { it.key })
            .containsExactly(
                InterfaceSettingsKey,
                PlaybackSettingsKey,
                StorageSettingsKey,
                AdvancedSettingsKey,
                DownloadsKey,
                ProvidersSettingsKey,
                null,
                BackupSettingsKey,
                AboutSettingsKey,
                null,
                null,
            ).inOrder()
    }

    @Test
    fun `rows are single clickable nodes and group titles are headings`() {
        show()

        // Title and click on one merged node: TalkBack reads the row as one control.
        composeRule.onNode(row("Interface")).assertExists()
        composeRule.onNode(isHeading() and hasText("Settings")).assertExists()
        composeRule.onNode(isHeading() and hasText("Miscellaneous")).assertExists()
    }

    @Test
    fun `back leaves the hub`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(back).isEqualTo(1)
    }
}
