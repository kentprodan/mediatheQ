plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// Album and playlist pages on TV (#238): one track list layout for both.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.album"
}
