package io.github.kentprodan.mediatheq.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeKind
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class HomeSectionSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val section = HomeSection.FavoriteAlbums

    private fun album(
        name: String,
        favorite: Boolean,
    ) = Album(MediaId(ServerId("s"), name), name, emptyList(), userData = UserData(isFavorite = favorite))

    private val viewModel by lazy {
        HomeSectionSettingsViewModel(
            section,
            library,
            preferences,
            HomeShuffle(1),
            TestClock(Instant.parse("2026-11-12T00:00:00Z")),
            mainDispatcherRule.testDispatcher,
        )
    }

    private val content get() = viewModel.uiState.value as HomeSectionSettingsUiState.Content

    private fun test(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album("a", favorite = true), album("b", favorite = false), album("c", true))
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
            block()
        }

    @Test
    fun `the preview holds the section's real items, and the sections of the same kind to copy from`() =
        test {
            assertThat((content.preview.items as HomeItems.Albums).albums.map { it.title }).containsExactly("a", "c")
            assertThat(content.settings).isEqualTo(section.defaultSettings)
            assertThat(content.copyFrom).doesNotContain(section)
            assertThat(content.copyFrom.map { it.kind }.toSet()).containsExactly(HomeKind.Albums)
        }

    @Test
    fun `two quick changes both land, stored at once, and the preview follows`() =
        test {
            viewModel.onAction(HomeSectionSettingsAction.Set { it.copy(display = HomeDisplay.Grid) })
            viewModel.onAction(HomeSectionSettingsAction.Set { it.copy(maxItems = 5) })
            runCurrent()

            assertThat(
                preferences.preferences
                    .first()
                    .home
                    .settingsOf(section)
                    .display,
            ).isEqualTo(HomeDisplay.Grid)
            assertThat(content.settings.maxItems).isEqualTo(5)
        }

    @Test
    fun `copy from takes another section's settings, apply to all gives these to the kind, restore resets this one`() =
        test {
            val other = HomeSection.RandomAlbums
            preferences.setHomeSectionSettings(other, other.defaultSettings.copy(maxItems = 30, playButton = true))

            viewModel.onAction(HomeSectionSettingsAction.CopyFrom(other))
            runCurrent()
            assertThat(content.settings.maxItems).isEqualTo(30)

            viewModel.onAction(HomeSectionSettingsAction.ApplyToAll)
            runCurrent()
            val home = preferences.preferences.first().home
            assertThat(home.settingsOf(HomeSection.LastPlayedAlbums).playButton).isTrue()
            assertThat(home.settingsOf(HomeSection.RandomArtists)).isEqualTo(HomeSection.RandomArtists.defaultSettings)

            viewModel.onAction(HomeSectionSettingsAction.Restore)
            runCurrent()
            assertThat(content.settings).isEqualTo(section.defaultSettings)
            assertThat(
                preferences.preferences
                    .first()
                    .home
                    .settingsOf(other)
                    .maxItems,
            ).isEqualTo(30)
        }
}
