package io.github.kentprodan.mediatheq.feature.settings

import java.net.URLEncoder

/** What Settings -> About shows of the app and the phone (#151); the app builds it. Nothing about accounts. */
data class AboutInfo(
    val versionName: String,
    val versionCode: Long,
    /** "17" */
    val androidVersion: String,
    /** "Google Pixel 9" */
    val device: String,
    /** Debug builds open the theme showcase. */
    val debug: Boolean = false,
) {
    /** "mediatheQ 0.1.0 (1), Android 17, Google Pixel 9": copied from the Version row and sent with a support issue. */
    fun summary(): String = "mediatheQ $versionName ($versionCode), Android $androidVersion, $device"
}

/** The GitHub repository: the source code, and where support issues go. */
const val SOURCE_URL = "https://github.com/kentprodan/mediatheQ"

/** Contact support (#151): a feature request or a problem report, each with its GitHub issue template. */
enum class SupportKind(
    val template: String,
) {
    FeatureRequest("feature_request.yml"),
    Issue("bug.yml"),
}

/** A new GitHub issue with [kind]'s template, its `device` field filled with [info]'s summary (nothing personal). */
fun supportIssueUrl(
    kind: SupportKind,
    info: AboutInfo,
): String = "$SOURCE_URL/issues/new?template=${kind.template.encoded()}&device=${info.summary().encoded()}"

private fun String.encoded() = URLEncoder.encode(this, "UTF-8")
