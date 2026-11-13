package io.github.kentprodan.mediatheq.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme

/** The TV app's one activity (ADR 0038). Back on the placeholder leaves the app (the activity default). */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediatheqTvTheme { PlaceholderScreen() } }
    }
}
