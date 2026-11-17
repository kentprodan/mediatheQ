plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// Video on TV (TV 2 · Video): the Movies and Series grids (#426); the pages and the player come next.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.video"
}

dependencies {
    // Back in the player hides the bar first (BackHandler).
    implementation(libs.androidx.activity.compose)
    // Chapter pictures (#430).
    implementation(libs.coil.compose)
}
