plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// Settings on TV (#243): Dolby Atmos passthrough, streaming quality, Sync now, the account, version and licenses.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.settings"
}
