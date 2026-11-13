plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// The artist page on TV (#239).
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.artist"
}
