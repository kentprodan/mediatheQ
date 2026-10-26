package io.github.kentprodan.mediatheq.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

/** SDK levels and JDK toolchain shared by every Android module (ADR 0003, ADR 0010). */
internal fun Project.configureAndroid(android: CommonExtension) {
    configureSdkAndToolchain(android)
    configureKotlin()
}

/** SDK levels and the JDK toolchain only: test modules (`com.android.test`) have no unit tests to configure. */
internal fun Project.configureSdkAndToolchain(android: CommonExtension) {
    android.compileSdk = libs.intVersion("compileSdk")
    android.defaultConfig.minSdk = libs.intVersion("minSdk")
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(libs.intVersion("jdk"))
    }
}

/** JDK toolchain and the standard unit test libraries (testing skill) for every Kotlin module. */
internal fun Project.configureKotlin() {
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(libs.intVersion("jdk"))
    }
    dependencies {
        add("testImplementation", libs.findLibrary("junit").get())
        add("testImplementation", libs.findLibrary("truth").get())
    }
}
