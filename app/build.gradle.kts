plugins {
    alias(libs.plugins.mediatheq.android.application)
    alias(libs.plugins.mediatheq.android.hilt)
}

android {
    namespace = "io.github.kentprodan.mediatheq"

    defaultConfig {
        applicationId = "io.github.kentprodan.mediatheq"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
