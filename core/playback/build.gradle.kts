plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.playback"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.data)
}
