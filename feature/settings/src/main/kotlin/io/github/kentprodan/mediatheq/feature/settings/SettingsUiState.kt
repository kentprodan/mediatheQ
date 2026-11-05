package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo

/** What Settings and its Audio page show. */
sealed interface SettingsUiState {
    /** Until the account and the preferences are read. */
    data object Loading : SettingsUiState

    data class Content(
        val serverName: String,
        val userName: String,
        val audio: AudioPreferences = AudioPreferences(),
        val library: LibraryPreferences = LibraryPreferences(),
        /** What the output does with the audio settings (bit-perfect, why the equalizer is unavailable). */
        val output: AudioOutputInfo = AudioOutputInfo(),
        /** The "Organize your navigation" dialog while it is open (#122). */
        val entriesDraft: NavEntriesDraft? = null,
        /** The Library page's "Organize" dialog while it is open (#129). */
        val shortcutsDraft: OrganizeDraft<LibraryShortcut>? = null,
        /** The Album page's "Page content order" dialog while it is open (#132). */
        val sectionsDraft: OrganizeDraft<AlbumSection>? = null,
        /** The Artist page's "Page content order" dialog while it is open (#133). */
        val artistSectionsDraft: OrganizeDraft<ArtistSection>? = null,
    ) : SettingsUiState {
        /** Downloads left the navigation: the hub offers "Manage offline files" instead (#122). */
        val showsManageOfflineFiles: Boolean get() = MainNavEntry.Downloads !in library.navigation.entries
    }
}

/**
 * Items being organized in an "Organize" dialog: every item in its [order], the [checked] ones shown. [fixed] ones
 * cannot be unchecked (Home in the navigation, #122); at least one stays checked, at most [max].
 */
data class OrganizeDraft<T>(
    val order: List<T>,
    val checked: Set<T>,
    val fixed: Set<T> = emptySet(),
    val max: Int = Int.MAX_VALUE,
) {
    /** The checked items in order: what OK stores. */
    val entries: List<T> get() = order.filter { it in checked }

    /** [item] can be checked or unchecked now. */
    fun canToggle(item: T): Boolean = item !in fixed && if (item in checked) checked.size > 1 else checked.size < max

    fun toggle(item: T): OrganizeDraft<T> =
        when {
            !canToggle(item) -> this
            item in checked -> copy(checked = checked - item)
            else -> copy(checked = checked + item)
        }

    fun move(
        from: Int,
        to: Int,
    ): OrganizeDraft<T> =
        if (from !in order.indices || to !in order.indices) {
            this
        } else {
            copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
        }

    companion object {
        /** The stored [entries] first, in their order, then the rest of [all]. */
        fun <T> of(
            entries: List<T>,
            all: List<T>,
            fixed: Set<T> = emptySet(),
            max: Int = Int.MAX_VALUE,
        ) = OrganizeDraft(entries + (all - entries.toSet()), entries.toSet(), fixed, max)
    }
}

/** The navigation entries being organized (#122): Home always checked; at most [NavigationPreferences.MAX_ENTRIES]. */
typealias NavEntriesDraft = OrganizeDraft<MainNavEntry>

fun navEntriesDraft(entries: List<MainNavEntry>): NavEntriesDraft =
    OrganizeDraft.of(entries, MainNavEntry.entries, setOf(MainNavEntry.Home), NavigationPreferences.MAX_ENTRIES)

sealed interface SettingsAction {
    data object LogOut : SettingsAction

    /** The quality on [metered] networks (mobile data), or on unmetered ones (Wi-Fi). */
    data class SetStreamingQuality(
        val metered: Boolean,
        val quality: StreamingQuality,
    ) : SettingsAction

    data class SetNormalization(
        val normalization: Normalization,
    ) : SettingsAction

    data class SetStreamingCacheGb(
        val gb: Int,
    ) : SettingsAction

    data class SetDownloadsUnmeteredOnly(
        val on: Boolean,
    ) : SettingsAction

    /** Replaces every theme setting (#121); the screen sends the current ones with one changed. */
    data class SetTheme(
        val theme: ThemePreferences,
    ) : SettingsAction

    data object RestoreThemeDefaults : SettingsAction

    /** Replaces every navigation and media start setting (#122); the screen sends the current ones with one changed. */
    data class SetNavigation(
        val navigation: NavigationPreferences,
    ) : SettingsAction

    data object RestoreNavigationDefaults : SettingsAction

    /** Replaces every Media lists setting (#123); the screen sends the current ones with one changed. */
    data class SetMediaLists(
        val mediaLists: MediaListPreferences,
    ) : SettingsAction

    data object RestoreMediaListsDefaults : SettingsAction

    data class SetNowPlaying(
        val nowPlaying: NowPlayingPreferences,
    ) : SettingsAction

    data object RestoreNowPlayingDefaults : SettingsAction

    /** Home page (#127): the sections in this order. */
    data class SetHomeOrder(
        val order: List<HomeSection>,
    ) : SettingsAction

    data class SetHomeHidden(
        val hidden: Set<HomeSection>,
    ) : SettingsAction

    /** Home page -> Reset active rows: order and visibility back to default. */
    data object ResetHomeRows : SettingsAction

    data object RestoreHomeDefaults : SettingsAction

    /** Opens "Organize your navigation" with the stored entries. */
    data object OpenNavEntries : SettingsAction

    data class ToggleNavEntry(
        val entry: MainNavEntry,
    ) : SettingsAction

    data class MoveNavEntry(
        val from: Int,
        val to: Int,
    ) : SettingsAction

    /** OK: stores the checked entries in their order. */
    data object ConfirmNavEntries : SettingsAction

    /** Cancel: nothing changes. */
    data object DismissNavEntries : SettingsAction

    /** Library page (#129): every value of the page at once. */
    data class SetLibraryPage(
        val page: LibraryPagePreferences,
    ) : SettingsAction

    data object RestoreLibraryPageDefaults : SettingsAction

    /** Opens the Library page's "Organize" dialog with the stored tiles. */
    data object OpenLibraryShortcuts : SettingsAction

    data class ToggleLibraryShortcut(
        val shortcut: LibraryShortcut,
    ) : SettingsAction

    data class MoveLibraryShortcut(
        val from: Int,
        val to: Int,
    ) : SettingsAction

    data object ConfirmLibraryShortcuts : SettingsAction

    data object DismissLibraryShortcuts : SettingsAction

    /** Album page (#132): every value of the page at once. */
    data class SetAlbumPage(
        val page: AlbumPagePreferences,
    ) : SettingsAction

    data object RestoreAlbumPageDefaults : SettingsAction

    /** Opens "Page content order" with the stored order. */
    data object OpenAlbumSections : SettingsAction

    data class MoveAlbumSection(
        val from: Int,
        val to: Int,
    ) : SettingsAction

    data object ConfirmAlbumSections : SettingsAction

    data object DismissAlbumSections : SettingsAction

    /** Artist page (#133): every value of the page at once. */
    data class SetArtistPage(
        val page: ArtistPagePreferences,
    ) : SettingsAction

    data object RestoreArtistPageDefaults : SettingsAction

    data object OpenArtistSections : SettingsAction

    data class MoveArtistSection(
        val from: Int,
        val to: Int,
    ) : SettingsAction

    data object ConfirmArtistSections : SettingsAction

    data object DismissArtistSections : SettingsAction

    data class SetOfflineMode(
        val on: Boolean,
    ) : SettingsAction

    data class SetAlbumColumns(
        val columns: Int,
    ) : SettingsAction

    data class SetRatingMode(
        val mode: RatingMode,
    ) : SettingsAction

    data class SetBitPerfectUsb(
        val on: Boolean,
    ) : SettingsAction

    data class SetEqualizerOn(
        val on: Boolean,
    ) : SettingsAction

    data class SetEqPreset(
        val preset: EqPreset,
    ) : SettingsAction

    /** Set equalizer band [index] to [gainDb]. */
    data class SetEqBand(
        val index: Int,
        val gainDb: Float,
    ) : SettingsAction
}
