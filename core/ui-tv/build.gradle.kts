plugins {
    alias(libs.plugins.mediatheq.android.library.compose)
    alias(libs.plugins.mediatheq.android.screenshot)
    alias(libs.plugins.mediatheq.android.tv)
}

// Domain-aware TV composables (ADR 0037): the TV design system plus the model; never :core:ui.
android {
    namespace = "io.github.kentprodan.mediatheq.core.ui.tv"
}

dependencies {
    api(projects.core.designsystemTv)
    api(projects.core.model)
    // Covers: an Artwork is the request's data; the app's image loader turns it into a URL (ArtworkInterceptor).
    implementation(libs.coil.compose)
}
