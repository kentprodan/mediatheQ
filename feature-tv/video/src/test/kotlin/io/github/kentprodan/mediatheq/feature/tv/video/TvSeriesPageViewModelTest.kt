package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TvSeriesPageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val videos = FakeVideoRepository()
    private val userData = FakeUserDataRepository()
    private val preferences = FakeVideoPreferencesRepository()
    private val show = Series(id("sr"), "Show")
    private val specials = Season(id("s0"), show.id, "Specials", 0)
    private val one = Season(id("s1"), show.id, "Season 1", 1)
    private val two = Season(id("s2"), show.id, "Season 2", 2)

    private fun episode(
        name: String,
        season: Season,
        number: Int,
        watched: Boolean = false,
    ) = Video(
        id(name),
        VideoKind.Episode,
        name,
        seriesId = show.id,
        seasonId = season.id,
        seasonNumber = season.number,
        episodeNumber = number,
        userData = UserData(played = watched),
    )

    private fun TestScope.viewModel(): TvSeriesPageViewModel {
        val viewModel = TvSeriesPageViewModel(show.id, videos, userData, preferences)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TvSeriesPageViewModel.content() = uiState.value as TvSeriesPageUiState.Content

    @Test
    fun `specials go last, the page opens on next up's season, a season is marked only after a yes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val s1e1 = episode("s1e1", one, 1, watched = true)
            val s2e1 = episode("s2e1", two, 1)
            videos.series.value = listOf(show)
            videos.seriesSeasons.value = mapOf(show.id to listOf(specials, one, two))
            videos.seasonEpisodes.value =
                mapOf(specials.id to listOf(episode("sp", specials, 1)), one.id to listOf(s1e1), two.id to listOf(s2e1))
            val viewModel = viewModel()

            val content = viewModel.content()
            assertThat(content.seasons).containsExactly(one, two, specials).inOrder()
            assertThat(content.season).isEqualTo(two)
            assertThat(content.firstEpisode?.id).isEqualTo(s1e1.id)
            assertThat(episodeCode(s2e1)).isEqualTo("S2 · E1")

            viewModel.onAction(TvSeriesPageAction.AskWatched(TvWatchedTarget.Season(two, watched = true)))
            runCurrent()
            assertThat(userData.calls).isEmpty()
            viewModel.onAction(TvSeriesPageAction.ConfirmWatched)
            runCurrent()
            assertThat(userData.calls).containsExactly("played:s2:true")
            assertThat(viewModel.content().confirm).isNull()

            viewModel.onAction(TvSeriesPageAction.ToggleHideOverviews)
            runCurrent()
            assertThat(viewModel.content().hideUnwatchedOverviews).isTrue()
        }
}
