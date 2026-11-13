plugins {
    alias(libs.plugins.mediatheq.android.feature.tv)
    alias(libs.plugins.mediatheq.android.screenshot)
}

// Search on TV (#244): the library as rows of results while typing, recent searches when the field is empty.
android {
    namespace = "io.github.kentprodan.mediatheq.feature.tv.search"
}

dependencies {
    // Back from the results returns to the field (BackHandler: predictive back never sends the Back key to views).
    implementation(libs.androidx.activity.compose)
}
