package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.model.allowed
import org.junit.Test

class AppModeFiltersTest {
    private val always = listOf(MainNavEntry.Home, MainNavEntry.Library, MainNavEntry.Search, MainNavEntry.Downloads)

    @Test
    fun `Video mode allows only Home, Library, Search and Downloads`() {
        assertThat(MainNavEntry.entries.filter { it.allowed(AppMode.Video) }).containsExactlyElementsIn(always)
    }

    @Test
    fun `Music and Both allow every entry`() {
        assertThat(MainNavEntry.entries.all { it.allowed(AppMode.Music) && it.allowed(AppMode.Both) }).isTrue()
    }

    @Test
    fun `chosen entries stay when the mode allows them all`() {
        val chosen = NavigationPreferences(entries = listOf(MainNavEntry.Home, MainNavEntry.Albums))

        assertThat(chosen.entriesFor(AppMode.Both)).isEqualTo(chosen.entries)
        assertThat(chosen.entriesFor(AppMode.Music)).isEqualTo(chosen.entries)
    }

    @Test
    fun `Video mode falls back to the defaults when a chosen entry is music`() {
        val chosen = NavigationPreferences(entries = listOf(MainNavEntry.Home, MainNavEntry.Albums))

        assertThat(chosen.entriesFor(AppMode.Video)).isEqualTo(always)
    }

    @Test
    fun `Video mode keeps chosen entries it allows`() {
        val chosen = NavigationPreferences(entries = listOf(MainNavEntry.Home, MainNavEntry.Search))

        assertThat(chosen.entriesFor(AppMode.Video)).isEqualTo(chosen.entries)
    }

    @Test
    fun `Home sections are music only for now`() {
        assertThat(HomeSection.entries.none { it.allowed(AppMode.Video) }).isTrue()
        assertThat(HomeSection.entries.all { it.allowed(AppMode.Music) && it.allowed(AppMode.Both) }).isTrue()
    }

    @Test
    fun `Library tiles per mode`() {
        val video = listOf(LibraryShortcut.Movies, LibraryShortcut.Series)

        assertThat(LibraryShortcut.entries.filter { it.allowed(AppMode.Video) }).isEqualTo(video)
        assertThat(LibraryShortcut.entries.filter { it.allowed(AppMode.Music) })
            .isEqualTo(LibraryShortcut.entries - video.toSet())
        assertThat(LibraryShortcut.entries.all { it.allowed(AppMode.Both) }).isTrue()
    }
}
