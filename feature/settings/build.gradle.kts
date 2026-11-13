plugins {
    alias(libs.plugins.mediatheq.android.feature)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.feature.settings"
}

dependencies {
    // The system file picker for backups (#150).
    implementation(libs.androidx.activity.compose)
}
