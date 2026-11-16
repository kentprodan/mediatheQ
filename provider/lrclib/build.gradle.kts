plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// LRCLIB (lrclib.net): synced lyrics for tracks the media server has none for (#381).
android {
    namespace = "io.github.kentprodan.mediatheq.provider.lrclib"
}

dependencies {
    implementation(projects.core.server)
    implementation(projects.core.network)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
