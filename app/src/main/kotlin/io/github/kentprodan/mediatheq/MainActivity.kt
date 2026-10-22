package io.github.kentprodan.mediatheq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.feature.album.albumEntry
import io.github.kentprodan.mediatheq.feature.artist.artistEntry
import io.github.kentprodan.mediatheq.feature.library.libraryEntry
import io.github.kentprodan.mediatheq.feature.nowplaying.NowPlayingHost
import io.github.kentprodan.mediatheq.feature.playlist.addToPlaylistEntry
import io.github.kentprodan.mediatheq.feature.playlist.playlistEntry
import io.github.kentprodan.mediatheq.navigation.AppShell
import io.github.kentprodan.mediatheq.navigation.OnboardingHost
import io.github.kentprodan.mediatheq.navigation.placeholders
import io.github.kentprodan.mediatheq.navigation.rememberTabNavigator

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediatheqTheme {
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
                        val entries =
                            remember(navigator) {
                                entryProvider {
                                    libraryEntry(navigator)
                                    albumEntry(navigator)
                                    artistEntry(navigator)
                                    playlistEntry(navigator)
                                    addToPlaylistEntry(navigator)
                                    placeholders(navigator, onLogout = viewModel::logout, includeLibrary = false)
                                }
                            }
                        NowPlayingHost(content = { miniPlayer ->
                            AppShell(navigator, entries, miniPlayer = miniPlayer)
                        })
                    }
                }
            }
        }
    }
}
