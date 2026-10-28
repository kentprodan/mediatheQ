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
    // Downloadable fonts through Play services (ADR 0026).
    implementation(libs.androidx.compose.ui.text.google.fonts)
}
