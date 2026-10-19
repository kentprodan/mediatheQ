plugins {
    alias(libs.plugins.mediatheq.jvm.library)
}

dependencies {
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
