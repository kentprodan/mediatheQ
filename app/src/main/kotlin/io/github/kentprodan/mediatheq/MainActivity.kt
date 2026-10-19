package io.github.kentprodan.mediatheq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.designsystem.preview.ThemeShowcase
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Placeholder until the navigation shell (#6) exists.
        setContent {
            MediatheqTheme {
                ThemeShowcase()
            }
        }
    }
}
