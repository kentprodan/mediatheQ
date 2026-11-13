plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)
    implementation(projects.core.server)
    implementation(projects.core.datastore)
    implementation(projects.core.database)
    implementation(libs.kotlinx.serialization.json)
    // ArtworkInterceptor: both apps' image loaders turn an Artwork into a server URL (ADR 0038).
    api(libs.coil.core)
    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
