plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
