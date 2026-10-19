plugins {
    alias(libs.plugins.mediatheq.android.library.compose)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    // Cover colors for the player (ADR 0017).
    implementation(libs.material.color.utilities)
}
