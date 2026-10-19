plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.kentprodan.mediatheq.provider.jellyfin"
}

dependencies {
    implementation(projects.core.server)
    implementation(projects.core.network)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
