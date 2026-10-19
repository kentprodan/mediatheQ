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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
