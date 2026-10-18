plugins {
    alias(libs.plugins.mediatheq.android.library.compose)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.ui"
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
}
