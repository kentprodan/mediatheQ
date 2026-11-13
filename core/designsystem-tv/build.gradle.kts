plugins {
    alias(libs.plugins.mediatheq.android.library.compose)
    alias(libs.plugins.mediatheq.android.screenshot)
    alias(libs.plugins.mediatheq.android.tv)
}

// The TV design system (ADR 0037): tv-material only, never :core:designsystem or androidx.compose.material3.
android {
    namespace = "io.github.kentprodan.mediatheq.core.designsystem.tv"
}

dependencies {
    api(libs.androidx.tv.material)
    // The scheme from the Iris seed, with the phone's generator (ADR 0012, 0037).
    implementation(libs.material.color.utilities)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
