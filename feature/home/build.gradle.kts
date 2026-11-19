plugins {
    alias(libs.plugins.mediatheq.android.feature)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.feature.home"
}

dependencies {
    // BackHandler: Back folds the Shuffle all / mixes FAB menu (#533).
    implementation(libs.androidx.activity.compose)
}
