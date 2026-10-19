plugins {
    alias(libs.plugins.mediatheq.android.library.compose)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
}
