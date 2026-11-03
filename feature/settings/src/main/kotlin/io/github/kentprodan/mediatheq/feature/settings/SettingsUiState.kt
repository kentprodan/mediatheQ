package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
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
    ) : SettingsUiState {
        /** Downloads left the navigation: the hub offers "Manage offline files" instead (#122). */
        val showsManageOfflineFiles: Boolean get() = MainNavEntry.Downloads !in library.navigation.entries
    }
}

/**
 * The navigation entries being organized (#122): every entry in its order, the checked ones shown in the navigation.
 * Home is always checked; at most [NavigationPreferences.MAX_ENTRIES].
 */
data class NavEntriesDraft(
    val order: List<MainNavEntry>,
    val checked: Set<MainNavEntry>,
) {
    /** The checked entries in order: what OK stores. */
    val entries: List<MainNavEntry> get() = order.filter { it in checked }

    /** [entry] can be checked or unchecked now. */
    fun canToggle(entry: MainNavEntry): Boolean =
        entry != MainNavEntry.Home && (entry in checked || checked.size < NavigationPreferences.MAX_ENTRIES)

    fun toggle(entry: MainNavEntry): NavEntriesDraft =
        when {
            !canToggle(entry) -> this
            entry in checked -> copy(checked = checked - entry)
            else -> copy(checked = checked + entry)
        }

    fun move(
        from: Int,
        to: Int,
    ): NavEntriesDraft =
        if (from !in order.indices || to !in order.indices) {
            this
        } else {
            copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
        }

    companion object {
        /** The stored [entries] first, in their order, then the others. */
        fun of(entries: List<MainNavEntry>) =
            NavEntriesDraft(entries + (MainNavEntry.entries - entries.toSet()), entries.toSet())
    }
}

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
