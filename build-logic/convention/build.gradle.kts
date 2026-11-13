plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "mediatheq.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "mediatheq.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "mediatheq.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "mediatheq.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "mediatheq.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidScreenshot") {
            id = "mediatheq.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
        register("androidTest") {
            id = "mediatheq.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidTv") {
            id = "mediatheq.android.tv"
            implementationClass = "AndroidTvConventionPlugin"
        }
        register("androidFeatureTv") {
            id = "mediatheq.android.feature.tv"
            implementationClass = "AndroidFeatureTvConventionPlugin"
        }
        register("androidHilt") {
            id = "mediatheq.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
