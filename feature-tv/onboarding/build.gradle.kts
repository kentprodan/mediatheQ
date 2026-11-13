plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// Signing in on the TV (ADR 0040): server address, Quick Connect first, password as the fallback.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.onboarding"
}
