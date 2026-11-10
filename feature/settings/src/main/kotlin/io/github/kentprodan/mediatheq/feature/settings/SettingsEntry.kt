package io.github.kentprodan.mediatheq.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AboutSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.AdvancedSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumPageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistPageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.AudioFocusSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.AutoActionsSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.DecodingSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.GenrePageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.HeadsetSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.HomePageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.InterfaceSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryPageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.LicensesKey
import io.github.kentprodan.mediatheq.core.navigation.MediaListsSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.NavigationSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.NotificationButtonsSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.OutputKey
import io.github.kentprodan.mediatheq.core.navigation.OutputSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.PlaybackBehaviorSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.PlaybackSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.PlaylistPageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.ProvidersSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.SearchPageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey
import io.github.kentprodan.mediatheq.core.navigation.StorageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.ThemeSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.TransitionsSettingsKey

/**
 * Registers the Settings hub, its pages, the Audio page and the open-source licenses. [versionName] is the app's;
 * [onOpenShowcase] opens the theme showcase, which lives in the app; [licensesReport] and [licenseNotices] read the
 * build's licenses report and the libraries' NOTICE files (ADR 0022).
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    navigator: Navigator,
    versionName: String,
    onOpenShowcase: () -> Unit,
    licensesReport: () -> String,
    licenseNotices: () -> String,
) {
    val back: () -> Unit = { navigator.back() }
    val openOutputs: () -> Unit = { navigator.navigateTo(OutputSettingsKey) }
    entry<SettingsKey> { SettingsHubRoute(onOpen = { navigator.navigateTo(it.key) }, onBack = back) }
    entry<InterfaceSettingsKey> {
        SettingsPageRoute(
            SettingsPage.Interface,
            openOutputs,
            back,
            onOpenTheme = { navigator.navigateTo(ThemeSettingsKey) },
            onOpenNavigation = { navigator.navigateTo(NavigationSettingsKey) },
            onOpenMediaLists = { navigator.navigateTo(MediaListsSettingsKey) },
            onOpenNowPlaying = { navigator.navigateTo(NowPlayingSettingsKey) },
            onOpenHomePage = { navigator.navigateTo(HomePageSettingsKey) },
            onOpenLibraryPage = { navigator.navigateTo(LibraryPageSettingsKey) },
            onOpenAlbumPage = { navigator.navigateTo(AlbumPageSettingsKey) },
            onOpenArtistPage = { navigator.navigateTo(ArtistPageSettingsKey) },
            onOpenGenrePage = { navigator.navigateTo(GenrePageSettingsKey) },
            onOpenPlaylistPage = { navigator.navigateTo(PlaylistPageSettingsKey) },
            onOpenSearchPage = { navigator.navigateTo(SearchPageSettingsKey) },
            onOpenAdvanced = { navigator.navigateTo(AdvancedSettingsKey) },
        )
    }
    entry<ThemeSettingsKey> { SettingsPageRoute(SettingsPage.Theme, openOutputs, back) }
    entry<NavigationSettingsKey> { SettingsPageRoute(SettingsPage.Navigation, openOutputs, back) }
    entry<MediaListsSettingsKey> { SettingsPageRoute(SettingsPage.MediaLists, openOutputs, back) }
    entry<NowPlayingSettingsKey> { SettingsPageRoute(SettingsPage.NowPlaying, openOutputs, back) }
    entry<HomePageSettingsKey> { SettingsPageRoute(SettingsPage.Home, openOutputs, back) }
    entry<LibraryPageSettingsKey> { SettingsPageRoute(SettingsPage.Library, openOutputs, back) }
    entry<AlbumPageSettingsKey> { SettingsPageRoute(SettingsPage.Album, openOutputs, back) }
    entry<ArtistPageSettingsKey> { SettingsPageRoute(SettingsPage.Artist, openOutputs, back) }
    entry<GenrePageSettingsKey> { SettingsPageRoute(SettingsPage.Genre, openOutputs, back) }
    entry<PlaylistPageSettingsKey> { SettingsPageRoute(SettingsPage.Playlist, openOutputs, back) }
    entry<SearchPageSettingsKey> { SettingsPageRoute(SettingsPage.Search, openOutputs, back) }
    entry<AdvancedSettingsKey> { SettingsPageRoute(SettingsPage.Advanced, openOutputs, back) }
    entry<PlaybackSettingsKey> {
        SettingsPageRoute(
            SettingsPage.Playback,
            openOutputs,
            back,
            onOpenBehavior = { navigator.navigateTo(PlaybackBehaviorSettingsKey) },
            onOpenDecoding = { navigator.navigateTo(DecodingSettingsKey) },
            onOpenAudioFocus = { navigator.navigateTo(AudioFocusSettingsKey) },
            onOpenHeadset = { navigator.navigateTo(HeadsetSettingsKey) },
            onOpenNotificationButtons = { navigator.navigateTo(NotificationButtonsSettingsKey) },
            onOpenAutoActions = { navigator.navigateTo(AutoActionsSettingsKey) },
            onOpenTransitions = { navigator.navigateTo(TransitionsSettingsKey) },
        )
    }
    entry<PlaybackBehaviorSettingsKey> { SettingsPageRoute(SettingsPage.PlaybackBehavior, openOutputs, back) }
    entry<DecodingSettingsKey> { SettingsPageRoute(SettingsPage.Decoding, openOutputs, back) }
    entry<AudioFocusSettingsKey> { SettingsPageRoute(SettingsPage.AudioFocus, openOutputs, back) }
    entry<HeadsetSettingsKey> { SettingsPageRoute(SettingsPage.Headset, openOutputs, back) }
    entry<NotificationButtonsSettingsKey> { SettingsPageRoute(SettingsPage.NotificationButtons, openOutputs, back) }
    entry<AutoActionsSettingsKey> { SettingsPageRoute(SettingsPage.AutoActions, openOutputs, back) }
    entry<TransitionsSettingsKey> { SettingsPageRoute(SettingsPage.Transitions, openOutputs, back) }
    entry<StorageSettingsKey> { SettingsPageRoute(SettingsPage.Storage, openOutputs, back) }
    entry<ProvidersSettingsKey> { SettingsPageRoute(SettingsPage.Providers, openOutputs, back) }
    entry<AboutSettingsKey> {
        AboutSettingsScreen(versionName, onOpenShowcase, onOpenLicenses = { navigator.navigateTo(LicensesKey) }, back)
    }
    entry<OutputSettingsKey> { OutputListRoute(onOpen = { navigator.navigateTo(OutputKey(it)) }, onBack = back) }
    entry<OutputKey> { OutputRoute(it.id, onBack = back) }
    entry<LicensesKey> { LicensesRoute(licensesReport, licenseNotices, onBack = back) }
}

/** The page a hub entry opens. */
internal val SettingsHubEntry.key: NavKey
    get() =
        when (this) {
            SettingsHubEntry.Interface -> InterfaceSettingsKey
            SettingsHubEntry.Playback -> PlaybackSettingsKey
            SettingsHubEntry.Storage -> StorageSettingsKey
            SettingsHubEntry.Advanced -> AdvancedSettingsKey
            SettingsHubEntry.ManageOfflineFiles -> DownloadsKey
            SettingsHubEntry.Providers -> ProvidersSettingsKey
            SettingsHubEntry.About -> AboutSettingsKey
        }
