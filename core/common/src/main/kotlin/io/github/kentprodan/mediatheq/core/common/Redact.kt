package io.github.kentprodan.mediatheq.core.common

/**
 * [text] without secrets (#169): query strings of URLs (they can carry `ApiKey`), `ApiKey` / `api_key` / `password`
 * / `secret` / token values (quoted or not), `Authorization` and `X-Emby-Token` header values, and the same keys in
 * JSON (`"AccessToken": "..."`, the Quick Connect `"Secret"`). Server addresses and paths stay (owner decision, #169).
 */
fun redact(text: String): String = RULES.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }

private val RULES =
    listOf(
        // JSON first ("AccessToken":"..."), escaped quotes included.
        Regex(
            """"(access_?token|token|api_?key|password|pw|secret)"\s*:\s*"(?:[^"\\]|\\.)*"""",
            RegexOption.IGNORE_CASE,
        ) to
            "\"$1\":\"<redacted>\"",
        // A URL keeps its scheme, host and path; its query and fragment go.
        Regex("""(https?|wss?)://([^\s?#"'<>]*)[?#][^\s"'<>]*""", RegexOption.IGNORE_CASE) to "$1://$2?<redacted>",
        Regex("""\b(authorization|x-emby-token|x-mediabrowser-token)\s*:\s*[^\r\n]+""", RegexOption.IGNORE_CASE) to
            "$1: <redacted>",
        // A password or secret may hold spaces: everything up to the next separator goes.
        Regex("""\b(password|pw|secret)\s*[=:]\s*("(?:[^"\\]|\\.)*"|[^&,;}\r\n]+)""", RegexOption.IGNORE_CASE) to
            "$1=<redacted>",
        // Keys and tokens, quoted or not (Token="..." of the MediaBrowser header, a data class's toString).
        Regex(
            """\b(api_?key|access_?token|token)\s*[=:]\s*("(?:[^"\\]|\\.)*"|[^\s&,;})]+)""",
            RegexOption.IGNORE_CASE,
        ) to
            "$1=<redacted>",
    )
