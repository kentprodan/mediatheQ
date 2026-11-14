plugins {
    alias(libs.plugins.mediatheq.android.feature)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.feature.video"
}

dependencies {
    // The player's window: back, orientation and system bars (ADR 0049).
    implementation(libs.androidx.activity.compose)
    // Chapter pictures and trickplay tiles (#277); the tiles carry the auth header (network headers).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
