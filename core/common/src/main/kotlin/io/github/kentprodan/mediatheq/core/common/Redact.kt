package io.github.kentprodan.mediatheq.core.common

/**
 * [text] without secrets (#169): query strings of URLs (they can carry `ApiKey`), `ApiKey` / `api_key` / `password`
 * values, `Authorization` and `X-Emby-Token` header values, `Token="..."` of the MediaBrowser header and
 * `"AccessToken": "..."` in JSON. Server addresses and paths stay (owner decision, #169).
 */
fun redact(text: String): String = RULES.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }

private val RULES =
    listOf(
        // A URL keeps its scheme, host and path; its query and fragment go.
        Regex("""(https?|wss?)://([^\s?#"'<>]*)[?#][^\s"'<>]*""", RegexOption.IGNORE_CASE) to "$1://$2?<redacted>",
        Regex("""\b(api_?key|password|pw)\s*[=:]\s*[^\s&,;"'}]+""", RegexOption.IGNORE_CASE) to "$1=<redacted>",
        Regex("""\b(authorization|x-emby-token|x-mediabrowser-token)\s*:\s*[^\r\n]+""", RegexOption.IGNORE_CASE) to
            "$1: <redacted>",
        Regex("""\bToken\s*=\s*"[^"]*"""", RegexOption.IGNORE_CASE) to "Token=\"<redacted>\"",
        Regex(""""(access_?token|token|api_?key|password|pw)"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE) to
            "\"$1\":\"<redacted>\"",
    )
