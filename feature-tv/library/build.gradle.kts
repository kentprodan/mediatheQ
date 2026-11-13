plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// The Library on TV (#237): tabs of cover grids, genre and year pages.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.library"
}
