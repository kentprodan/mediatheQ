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
        // The same version as the phone app (ADR 0038).
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.designsystemTv)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.ext.junit)
}
