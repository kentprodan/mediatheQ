plugins {
    alias(libs.plugins.mediatheq.android.application)
    alias(libs.plugins.mediatheq.android.hilt)
    alias(libs.plugins.mediatheq.android.screenshot)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(libs.androidx.navigation3.ui)
}
