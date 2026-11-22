import com.android.build.api.dsl.ApplicationExtension
import io.github.kentprodan.mediatheq.buildlogic.configureAndroid
import io.github.kentprodan.mediatheq.buildlogic.configureCompose
import io.github.kentprodan.mediatheq.buildlogic.intVersion
import io.github.kentprodan.mediatheq.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                configureCompose(this)
                defaultConfig.targetSdk = libs.intVersion("targetSdk")
                // English and Romanian (#447): the app's own language in Android Settings, from the res/values-* folders;
                // English is the default (res/resources.properties).
                androidResources.generateLocaleConfig = true
                // Only these languages: libraries' other translations would otherwise show in the language list.
                androidResources.localeFilters += setOf("en", "ro")
                buildTypes {
                    // R8 (#398): Compose and the app run optimized; names stay, so logs and crashes read as written.
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            rootProject.file("proguard-rules.pro"),
                        )
                    }
                    // The build to use every day: release, signed with the debug key and with the debug build's id, so
                    // it installs over a debug build and keeps its data.
                    create("daily") {
                        initWith(getByName("release"))
                        signingConfig = signingConfigs.getByName("debug")
                        matchingFallbacks += "release"
                    }
                }
            }
        }
}
