package io.github.kentprodan.mediatheq.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import io.github.kentprodan.mediatheq.core.navigation.TracksKey
import org.junit.Test

class TabNavigatorTest {
    private val navigator =
        TabNavigator(TopLevelTab.entries.associateWith { mutableListOf(it.rootKey) })

    private val detail1 = PlaceholderDetailKey("Library", 1)
    private val detail2 = PlaceholderDetailKey("Library", 2)

    @Test
    fun `starts on the Home tab with only its root`() {
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Home)
        assertThat(navigator.stack(TopLevelTab.Home)).containsExactly(HomeKey)
        assertThat(navigator.visibleTabs).containsExactly(TopLevelTab.Home)
    }

    @Test
    fun `navigateTo pushes onto the current tab only`() {
        navigator.select(TopLevelTab.Library)
        navigator.navigateTo(detail1)

        assertThat(navigator.stack(TopLevelTab.Library)).containsExactly(LibraryKey, detail1).inOrder()
        assertThat(navigator.stack(TopLevelTab.Home)).containsExactly(HomeKey)
    }

    @Test
    fun `switching tabs and back preserves the stack`() {
        navigator.select(TopLevelTab.Library)
        navigator.navigateTo(detail1)
        navigator.navigateTo(detail2)

        navigator.select(TopLevelTab.Search)
        navigator.select(TopLevelTab.Library)

        assertThat(navigator.stack(TopLevelTab.Library)).containsExactly(LibraryKey, detail1, detail2).inOrder()
    }

    @Test
    fun `re-selecting the current tab pops it to its root`() {
        navigator.select(TopLevelTab.Library)
        navigator.navigateTo(detail1)
        navigator.navigateTo(detail2)

        navigator.select(TopLevelTab.Library)

        assertThat(navigator.stack(TopLevelTab.Library)).containsExactly(LibraryKey)
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Library)
    }

    @Test
    fun `back pops the current stack first`() {
        navigator.select(TopLevelTab.Library)
        navigator.navigateTo(detail1)

        assertThat(navigator.back()).isTrue()
        assertThat(navigator.stack(TopLevelTab.Library)).containsExactly(LibraryKey)
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Library)
    }

    @Test
    fun `back at a tab root returns to Home`() {
        navigator.select(TopLevelTab.Search)
        assertThat(navigator.visibleTabs).containsExactly(TopLevelTab.Home, TopLevelTab.Search).inOrder()

        assertThat(navigator.back()).isTrue()
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Home)
        assertThat(navigator.stack(TopLevelTab.Search)).containsExactly(SearchKey)
    }

    @Test
    fun `back at the Home root is not handled`() {
        assertThat(navigator.back()).isFalse()
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Home)
    }

    @Test
    fun `a removed selected tab falls back to Home, and its stack is kept for later`() {
        var tabs by mutableStateOf(listOf(TopLevelTab.Home, TopLevelTab.Tracks, TopLevelTab.Search))
        val navigator =
            TabNavigator(
                TopLevelTab.entries.associateWith { mutableListOf(it.rootKey) },
                mutableStateOf(TopLevelTab.Home),
            ) {
                tabs
            }
        navigator.select(TopLevelTab.Tracks)
        navigator.navigateTo(detail1)

        tabs = listOf(TopLevelTab.Home, TopLevelTab.Search)
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Home)
        assertThat(navigator.visibleTabs).containsExactly(TopLevelTab.Home)

        tabs = listOf(TopLevelTab.Home, TopLevelTab.Search, TopLevelTab.Tracks)
        navigator.select(TopLevelTab.Search)
        navigator.select(TopLevelTab.Tracks)
        assertThat(navigator.stack(TopLevelTab.Tracks)).containsExactly(TracksKey, detail1).inOrder()
    }

    @Test
    fun `changing the tabs keeps the stacks of the remaining ones`() {
        var tabs by mutableStateOf(DefaultTabs)
        val navigator =
            TabNavigator(
                TopLevelTab.entries.associateWith { mutableListOf(it.rootKey) },
                mutableStateOf(TopLevelTab.Home),
            ) {
                tabs
            }
        navigator.select(TopLevelTab.Library)
        navigator.navigateTo(detail1)

        tabs = listOf(TopLevelTab.Home, TopLevelTab.Albums, TopLevelTab.Library)

        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.Library)
        assertThat(navigator.stack(TopLevelTab.Library)).containsExactly(LibraryKey, detail1).inOrder()
    }

    @Test
    fun `every navigation entry has its tab`() {
        assertThat(MainNavEntry.entries.map { TopLevelTab.of(it).name }).isEqualTo(MainNavEntry.entries.map { it.name })
    }
}
