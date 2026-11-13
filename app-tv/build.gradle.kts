plugins {
    alias(libs.plugins.mediatheq.android.application)
    alias(libs.plugins.mediatheq.android.hilt)
    alias(libs.plugins.mediatheq.android.screenshot)
    alias(libs.plugins.mediatheq.android.tv)
    alias(libs.plugins.aboutlibraries.android)
}

// The TV app (ADR 0038): its own APK, the shared core, the TV design system; never the phone UI (checked on build).
aboutLibraries {
    offlineMode = true
    // The same license texts as the phone app (ADR 0022).
    collect.configPath = rootProject.file("app/aboutlibraries")
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "BSD-3-Clause", "MIT", "GPL-3.0-or-later")
        additionalLicenses.add("GPL-3.0-or-later")
    }
}

android {
    namespace = "io.github.kentprodan.mediatheq.tv"

    defaultConfig {
        applicationId = "io.github.kentprodan.mediatheq.tv"
        versionCode = 1
        versionName = libs.versions.mediatheqVersionName.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.designsystemTv)
    implementation(projects.core.uiTv)
    // The shared core (ADR 0038): one repository layer and provider for both apps.
    implementation(projects.core.common)
    implementation(projects.core.navigation)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.server)
    implementation(projects.core.datastore)
    implementation(projects.core.database)
    implementation(projects.core.playback)
    // The download cache the player and the sync need (ADR 0038); no Downloads screen on TV 1.
    implementation(projects.core.download)
    implementation(projects.provider.jellyfin)
    implementation(projects.featureTv.onboarding)
    implementation(projects.featureTv.home)
    implementation(projects.featureTv.library)
    implementation(projects.featureTv.album)
    implementation(projects.featureTv.artist)
    implementation(projects.featureTv.nowplaying)
    // Covers through the app's OkHttp client (ADR 0013), like the phone.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.media3.session)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
