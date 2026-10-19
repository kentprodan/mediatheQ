plugins {
    alias(libs.plugins.mediatheq.android.feature)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.feature.nowplaying"
}

dependencies {
    // Back closes the full player (BackHandler).
    implementation(libs.androidx.activity.compose)
}
