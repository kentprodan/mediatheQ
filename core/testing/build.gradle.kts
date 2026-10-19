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
    api(projects.core.database)
    // Host natives so database tests run on the JVM.
    api(libs.androidx.sqlite.bundled.jvm)
    api(libs.androidx.datastore.preferences)
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
