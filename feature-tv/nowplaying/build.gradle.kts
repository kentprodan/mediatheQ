plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// The full-screen player and its queue on TV (#240).
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.nowplaying"
}
