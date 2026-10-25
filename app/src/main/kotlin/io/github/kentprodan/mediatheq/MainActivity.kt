package io.github.kentprodan.mediatheq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.download.resumeDownloads
import io.github.kentprodan.mediatheq.feature.album.albumEntry
import io.github.kentprodan.mediatheq.feature.artist.artistEntry
import io.github.kentprodan.mediatheq.feature.downloads.downloadsEntry
import io.github.kentprodan.mediatheq.feature.home.homeEntry
import io.github.kentprodan.mediatheq.feature.library.libraryEntry
import io.github.kentprodan.mediatheq.feature.nowplaying.NowPlayingHost
import io.github.kentprodan.mediatheq.feature.playlist.addToPlaylistEntry
import io.github.kentprodan.mediatheq.feature.playlist.playlistEntry
import io.github.kentprodan.mediatheq.feature.search.searchEntry
import io.github.kentprodan.mediatheq.feature.settings.settingsEntry
import io.github.kentprodan.mediatheq.navigation.AppShell
import io.github.kentprodan.mediatheq.navigation.OnboardingHost
import io.github.kentprodan.mediatheq.navigation.ThemeShowcaseKey
import io.github.kentprodan.mediatheq.navigation.placeholders
import io.github.kentprodan.mediatheq.navigation.rememberTabNavigator

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Read from disk in a few milliseconds; until then nothing is drawn, rather than the wrong colors.
            val dynamicColor = viewModel.dynamicColor.collectAsStateWithLifecycle().value ?: return@setContent
            MediatheqTheme(useDynamicColor = dynamicColor) {
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
                            val navigator = rememberTabNavigator()
                            val snackbar = remember { SnackbarHostState() }
                            UserDataErrorMessages(viewModel.userDataError, viewModel::dismissUserDataError, snackbar)
                            val entries =
                                remember(navigator) {
                                    entryProvider {
                                        homeEntry(navigator)
                                        settingsEntry(
                                            navigator,
                                            versionName = versionName(),
                                            onOpenShowcase = { navigator.navigateTo(ThemeShowcaseKey) },
                                        )
                                        libraryEntry(navigator)
                                        albumEntry(navigator)
                                        artistEntry(navigator)
                                        playlistEntry(navigator)
                                        searchEntry(navigator)
                                        downloadsEntry()
                                        addToPlaylistEntry(navigator)
                                        placeholders(
                                            navigator,
                                            includeHome = false,
                                            includeLibrary = false,
                                            includeSearch = false,
                                            includeDownloads = false,
                                        )
                                    }
                                }
                            val online by viewModel.online.collectAsStateWithLifecycle()
                            NowPlayingHost(
                                content = { miniPlayer ->
                                    AppShell(
                                        navigator,
                                        entries,
                                        offline = !online,
                                        miniPlayer = miniPlayer,
                                        snackbarHostState = snackbar,
                                    )
                                },
                                snackbarHost = { SnackbarHost(snackbar) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The app's version, for Settings -> About. */
private fun android.app.Activity.versionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
