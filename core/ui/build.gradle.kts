plugins {
    alias(libs.plugins.mediatheq.android.library.compose)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.ui"
}

dependencies {
    api(projects.core.model)
    // Pure helpers (formatDuration, nameComparator, QualityLabel) moved down for the TV app too (ADR 0038).
    api(projects.core.common)
    api(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    // The video surface and its presentation state (ADR 0049); @UnstableApi, opted in only in MqVideoSurface.
    implementation(libs.androidx.media3.ui.compose)
    // SubtitleView: cues with the system caption style (ADR 0049) until Media3's Compose cues are stable.
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
}
