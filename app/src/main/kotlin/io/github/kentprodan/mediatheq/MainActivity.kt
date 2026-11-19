package io.github.kentprodan.mediatheq

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.common.FileAppLog
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.entriesFor
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPinDialog
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.download.resumeDownloads
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.RadioStarter
import io.github.kentprodan.mediatheq.core.playback.RemoteControl
import io.github.kentprodan.mediatheq.core.ui.LocalMediaListStyle
import io.github.kentprodan.mediatheq.feature.album.albumEntry
import io.github.kentprodan.mediatheq.feature.artist.artistEntry
import io.github.kentprodan.mediatheq.feature.downloads.downloadsEntry
import io.github.kentprodan.mediatheq.feature.genre.genreEntry
import io.github.kentprodan.mediatheq.feature.home.homeEntry
import io.github.kentprodan.mediatheq.feature.library.libraryEntry
import io.github.kentprodan.mediatheq.feature.nowplaying.NowPlayingHost
import io.github.kentprodan.mediatheq.feature.onboarding.appmode.AppModeRoute
import io.github.kentprodan.mediatheq.feature.onboarding.login.loginEntry
import io.github.kentprodan.mediatheq.feature.onboarding.quickconnect.quickConnectEntry
import io.github.kentprodan.mediatheq.feature.onboarding.server.serverEntry
import io.github.kentprodan.mediatheq.feature.playlist.addToPlaylistEntry
import io.github.kentprodan.mediatheq.feature.playlist.playlistEntry
import io.github.kentprodan.mediatheq.feature.search.searchEntry
import io.github.kentprodan.mediatheq.feature.settings.AboutInfo
import io.github.kentprodan.mediatheq.feature.settings.settingsEntry
import io.github.kentprodan.mediatheq.feature.video.videoEntry
import io.github.kentprodan.mediatheq.navigation.AppShell
import io.github.kentprodan.mediatheq.navigation.OnboardingHost
import io.github.kentprodan.mediatheq.navigation.ThemeShowcaseKey
import io.github.kentprodan.mediatheq.navigation.TopLevelTab
import io.github.kentprodan.mediatheq.navigation.placeholders
import io.github.kentprodan.mediatheq.navigation.rememberTabNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var log: FileAppLog

    @Inject lateinit var radio: RadioStarter

    @Inject lateinit var remoteControl: RemoteControl

    private val viewModel: MainViewModel by viewModels()

    // A new-episode notification (#446): the series it names.
    private val openSeries = MutableStateFlow<MediaId?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.series()?.let { openSeries.value = it }
    }

    private fun Intent.series(): MediaId? =
        getStringExtra(EXTRA_OPEN_SERIES)
            ?.takeIf { flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }
            ?.let { runCatching { MediaId.parse(it) }.getOrNull() }

    companion object {
        const val EXTRA_OPEN_SERIES = "io.github.kentprodan.mediatheq.OPEN_SERIES"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openSeries.value = intent.series()
        enableEdgeToEdge()
        setContent {
            // Read from disk in a few milliseconds; until then nothing is drawn, rather than the wrong colors.
            val theme = viewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            val advanced by viewModel.advanced.collectAsStateWithLifecycle()
            MediatheqTheme(theme) {
                WindowSettings(advanced)
                // Android 17: no server can be reached without local network access, so ask before anything else.
                LocalNetworkGate(
                    onAccess = { justGranted ->
                        // Unfinished downloads (after a reboot or a killed process) go on once the server can be
                        // reached (#20, #58): before that every attempt would time out and fail.
                        resumeDownloads(this@MainActivity)
                        if (justGranted) viewModel.onLocalNetworkGranted()
                    },
                ) {
                    val session by viewModel.session.collectAsStateWithLifecycle()
                    when (session) {
                        // Read from disk in a few milliseconds; a plain surface avoids flashing onboarding first.
                        null -> {
                            Surface(Modifier.fillMaxSize()) {}
                        }

                        is SessionState.LoggedOut -> {
                            OnboardingHost()
                        }

                        is SessionState.LoggedIn -> {
                            val asksAppMode by viewModel.asksAppMode.collectAsStateWithLifecycle()
                            if (asksAppMode) {
                                AppModeRoute()
                                return@LocalNetworkGate
                            }
                            val navigation by viewModel.navigation.collectAsStateWithLifecycle()
                            // Read in a few milliseconds; nothing is drawn meanwhile, rather than the other mode's shell.
                            val appMode =
                                viewModel.appMode.collectAsStateWithLifecycle().value ?: return@LocalNetworkGate
                            val navigator =
                                rememberTabNavigator(navigation.entriesFor(appMode).map(TopLevelTab::of))
                            val snackbar = remember { SnackbarHostState() }
                            UserDataErrorMessages(viewModel.userDataError, viewModel::dismissUserDataError, snackbar)
                            PlaybackErrorMessages(viewModel.playbackError, viewModel::dismissPlaybackError, snackbar)
                            RadioErrorMessages(radio.error, radio::dismissError, snackbar)
                            SlowStartMessages(viewModel.slowStart, viewModel::dismissSlowStart, snackbar)
                            // Downloads is optional in the navigation (#122); without its tab it opens on the current one.
                            val openDownloads = {
                                if (TopLevelTab.Downloads in navigator.tabs) {
                                    navigator.select(TopLevelTab.Downloads)
                                } else {
                                    navigator.navigateTo(DownloadsKey)
                                }
                            }
                            // Home's search bar (#533): the Search tab, or Search over Home without it.
                            val openSearch = {
                                if (TopLevelTab.Search in navigator.tabs) {
                                    navigator.select(TopLevelTab.Search)
                                } else {
                                    navigator.navigateTo(SearchKey)
                                }
                            }
                            val entries =
                                remember(navigator) {
                                    entryProvider {
                                        homeEntry(navigator, onOpenDownloads = openDownloads, onOpenSearch = openSearch)
                                        settingsEntry(
                                            navigator,
                                            about = aboutInfo(),
                                            onOpenShowcase = { navigator.navigateTo(ThemeShowcaseKey) },
                                            changelog = applicationContext::changelog,
                                            licensesReport = applicationContext::licensesReport,
                                            licenseNotices = applicationContext::licenseNotices,
                                            onExportLogs = ::exportLogs,
                                        )
                                        libraryEntry(navigator, onOpenDownloads = openDownloads)
                                        albumEntry(navigator)
                                        artistEntry(navigator)
                                        genreEntry(navigator)
                                        playlistEntry(navigator)
                                        searchEntry(navigator)
                                        videoEntry(navigator)
                                        // Opened from Settings on another tab (#149), it has a back button.
                                        downloadsEntry(navigator, isTab = {
                                            navigator.currentTab ==
                                                TopLevelTab.Downloads
                                        })
                                        addToPlaylistEntry(navigator)
                                        // Adding an account or signing in again from Settings (#147).
                                        serverEntry(navigator)
                                        loginEntry(navigator)
                                        quickConnectEntry(navigator)
                                        placeholders(
                                            navigator,
                                            includeHome = false,
                                            includeLibrary = false,
                                            includeSearch = false,
                                            includeDownloads = false,
                                        )
                                    }
                                }
                            // The age gate (#443): pages and players above the profile's limit wait for the PIN.
                            SideEffect { navigator.guard = viewModel::guard }
                            val pin by viewModel.pinRequest.collectAsStateWithLifecycle()
                            pin?.let { request ->
                                MqPinDialog(
                                    title = stringResource(R.string.pin_title),
                                    label = stringResource(R.string.pin_label),
                                    confirmLabel = stringResource(R.string.pin_ok),
                                    dismissLabel = stringResource(R.string.pin_cancel),
                                    onSubmit = viewModel::submitPin,
                                    onDismiss = viewModel::cancelPin,
                                    error = if (request.wrong) stringResource(R.string.pin_wrong) else null,
                                    onEdit = viewModel::pinEdited,
                                )
                            }
                            // A new-episode notification (#446) opens its series on the tab shown.
                            LaunchedEffect(navigator) {
                                openSeries.collect { id ->
                                    if (id != null) {
                                        navigator.navigateTo(SeriesKey(id))
                                        openSeries.value = null
                                    }
                                }
                            }
                            // A movie sent from another client (#389) plays already: its player opens on the tab shown.
                            LaunchedEffect(navigator) {
                                remoteControl.videoStarted.collect { id ->
                                    val stack = navigator.stack(navigator.currentTab)
                                    if (stack.lastOrNull() !is VideoPlayerKey) {
                                        navigator.navigateTo(
                                            VideoPlayerKey(id, attach = true),
                                        )
                                    }
                                }
                            }
                            val mediaListStyle by viewModel.mediaListStyle.collectAsStateWithLifecycle()
                            CompositionLocalProvider(LocalMediaListStyle provides mediaListStyle) {
                                NowPlayingHost(
                                    content = { miniPlayer ->
                                        AppShell(
                                            navigator,
                                            entries,
                                            miniPlayer = miniPlayer,
                                            snackbarHostState = snackbar,
                                            navigation = navigation,
                                        )
                                    },
                                    snackbarHost = { SnackbarHost(snackbar) },
                                    onNavigate = navigator::navigateTo,
                                    keepScreenOn = advanced.keepScreenOn,
                                    // Video mode (ADR 0051): only a playing video shows, as the mini player.
                                    videoOnly = !appMode.music,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings -> About -> Export logs (#169): the log, joined into one text file in the cache (the previous export goes),
 * offered through the share sheet. Nothing is sent without the user choosing where.
 */
private fun MainActivity.exportLogs() {
    lifecycleScope.launch {
        val file = File(File(cacheDir, "logs"), "mediatheq-log-${LocalDate.now()}.txt")
        if (!log.export(file)) {
            Toast.makeText(this@exportLogs, R.string.export_logs_failed, Toast.LENGTH_SHORT).show()
            return@launch
        }
        val uri = FileProvider.getUriForFile(this@exportLogs, "$packageName.logs", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, file.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }
}

/** The build's open-source licenses report (#70, ADR 0022), for Settings -> About. Read through the application, so
 * the screen's ViewModel never holds an Activity. */
private fun android.content.Context.licensesReport(): String =
    resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }

/** The libraries' NOTICE files the build collected (#87), for Settings -> About. */
private fun android.content.Context.licenseNotices(): String =
    resources.openRawResource(R.raw.license_notices).bufferedReader().use { it.readText() }

/** The bundled CHANGELOG.md (#151), for Settings -> About -> Changelog. */
private fun android.content.Context.changelog(): String =
    resources.openRawResource(R.raw.changelog).bufferedReader().use { it.readText() }

/** The app's version and the phone, for Settings -> About and Contact support (#151). */
private fun android.app.Activity.aboutInfo(): AboutInfo {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    // "Google" + "Pixel 9"; a model that already starts with its maker keeps its own name.
    val device =
        if (Build.MODEL.startsWith(
                Build.MANUFACTURER,
                ignoreCase = true,
            )
        ) {
            Build.MODEL
        } else {
            "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    return AboutInfo(
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = packageInfo.longVersionCode,
        androidVersion = Build.VERSION.RELEASE,
        device = device,
        debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    )
}
