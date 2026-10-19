plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.datastore"
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(projects.core.testing)
    // The Android Keystore exists only on a device (ADR 0013).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
}
