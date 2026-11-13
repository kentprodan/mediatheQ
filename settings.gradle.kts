pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Settings plugins cannot read the version catalog, so this version lives here.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "mediatheQ"
include(":app")
include(":app-tv")
include(":baselineprofile")
include(":core:model")
include(":core:common")
include(":core:testing")
include(":core:designsystem")
include(":core:designsystem-tv")
include(":core:ui-tv")
include(":core:ui")
include(":core:navigation")
include(":core:network")
include(":core:server")
include(":core:datastore")
include(":core:data")
include(":core:database")
include(":core:playback")
include(":core:download")
include(":provider:jellyfin")
include(":feature:onboarding")
include(":feature:library")
include(":feature:album")
include(":feature:artist")
include(":feature:genre")
include(":feature:nowplaying")
include(":feature:playlist")
include(":feature:search")
include(":feature:downloads")
include(":feature:home")
include(":feature:settings")
include(":feature:video")
include(":feature-tv:onboarding")
include(":feature-tv:home")
include(":feature-tv:library")
include(":feature-tv:album")
include(":feature-tv:artist")
include(":feature-tv:nowplaying")
include(":feature-tv:search")
include(":feature-tv:settings")
