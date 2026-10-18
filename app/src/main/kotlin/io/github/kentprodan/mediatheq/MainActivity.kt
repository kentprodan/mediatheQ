package io.github.kentprodan.mediatheq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.navigation.AppShell
import io.github.kentprodan.mediatheq.navigation.placeholderEntries
import io.github.kentprodan.mediatheq.navigation.rememberTabNavigator

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediatheqTheme {
                val navigator = rememberTabNavigator()
                val entries = remember(navigator) { placeholderEntries(navigator) }
                AppShell(navigator, entries)
            }
        }
    }
}
