plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// The Library on TV (#237): tabs of cover grids, genre and year pages.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.library"
}

dependencies {
    // Back from a list goes up to the tabs (#348; BackHandler, as in Search).
    implementation(libs.androidx.activity.compose)
}
