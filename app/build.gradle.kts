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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.playback)
    implementation(projects.core.download)
    implementation(projects.core.server)
    implementation(projects.core.network)
    implementation(projects.provider.jellyfin)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.library)
    implementation(projects.feature.album)
    implementation(projects.feature.artist)
    implementation(projects.feature.nowplaying)
    implementation(projects.feature.playlist)
    implementation(projects.feature.search)
    implementation(libs.androidx.lifecycle.process)
    implementation(projects.feature.downloads)
    implementation(projects.feature.home)
    implementation(projects.feature.settings)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.ui)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
