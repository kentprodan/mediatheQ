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
}
