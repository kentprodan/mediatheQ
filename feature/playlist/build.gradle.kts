plugins {
    alias(libs.plugins.mediatheq.android.feature)
    alias(libs.plugins.mediatheq.android.screenshot)
}

android {
    namespace = "io.github.kentprodan.mediatheq.feature.playlist"
}

dependencies {
    // DialogSceneStrategy for the "Add to playlist" dialog entry.
    implementation(libs.androidx.navigation3.ui)
}
