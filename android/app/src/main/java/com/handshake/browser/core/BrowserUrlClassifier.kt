package com.handshake.browser.core

import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class BrowserTargetKind {
    ExactUrl,
    HnsName,
    Search,
}

data class BrowserTarget(
    val kind: BrowserTargetKind,
    val url: String,
    val displayHost: String?,
)

class BrowserUrlClassifier(
    private val searchBaseUrl: String = "https://duckduckgo.com/?q=",
) {
    fun classify(input: String): BrowserTarget {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return search(trimmed)
        }

        if (trimmed.any(Char::isWhitespace)) {
            return search(trimmed)
        }

        val lower = trimmed.lowercase(Locale.US)
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return exact(trimmed)
        }

        if ("://" in trimmed) {
            return search(trimmed)
        }

        val hostCandidate = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostCandidate.isBlank()) {
            return search(trimmed)
        }

        val asciiHost = runCatching { IDN.toASCII(hostCandidate).lowercase(Locale.US) }.getOrNull()
            ?: return search(trimmed)

        if (!isValidHost(asciiHost)) {
            return search(trimmed)
        }

        val suffix = trimmed.removePrefix(hostCandidate)
        val normalizedSuffix = if (suffix.isEmpty()) "/" else suffix
        val kind = if (HnsHostPolicy.requiresHnsResolution(asciiHost)) {
            BrowserTargetKind.HnsName
        } else {
            BrowserTargetKind.ExactUrl
        }
        val scheme = "https"
        val url = "$scheme://$asciiHost$normalizedSuffix"
        return BrowserTarget(kind, url, asciiHost)
    }

    private fun exact(url: String): BrowserTarget {
        val host = runCatching { URI(url).host }.getOrNull()
            ?: url.substringAfter("://", "")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBeforeLast(':')
                .ifBlank { null }
        val kind = if (host?.let(HnsHostPolicy::requiresHnsResolution) == true) {
            BrowserTargetKind.HnsName
        } else {
            BrowserTargetKind.ExactUrl
        }
        return BrowserTarget(kind, url, host)
    }

    private fun search(query: String): BrowserTarget {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        return BrowserTarget(BrowserTargetKind.Search, searchBaseUrl + encoded, null)
    }

    private fun isValidHost(host: String): Boolean {
        if (host.length > 253 || host.startsWith(".") || host.endsWith(".")) {
            return false
        }

        return host.split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                !label.startsWith("-") &&
                !label.endsWith("-") &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
