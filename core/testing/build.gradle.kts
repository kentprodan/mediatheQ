plugins {
    alias(libs.plugins.mediatheq.android.library)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.testing"
}

dependencies {
    // Fakes of the :core:data interfaces live here (testing skill), so this is an Android library.
    api(projects.core.data)
    api(projects.core.datastore)
    api(projects.core.server)
    api(libs.androidx.datastore.preferences)
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
