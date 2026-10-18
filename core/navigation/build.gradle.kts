plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
}
