plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// Home on TV (#236): the phone's Home sections as rows of covers.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.home"
}
