package com.handshake.browser.net

import com.handshake.browser.core.HostnameAscii
import java.net.URI

internal fun URI.httpAuthorityHost(): String? {
    host?.let { return HostnameAscii.toAscii(it) }

    val authority = rawAuthority ?: return null
    if (authority.isBlank() || authority.contains('@')) {
        return null
    }

    val hostPart = if (authority.startsWith("[")) {
        val endBracket = authority.indexOf(']')
        if (endBracket <= 1) {
            return null
        }
        val remainder = authority.substring(endBracket + 1)
        if (remainder.isNotEmpty() && !isValidPortSuffix(remainder)) {
            return null
        }
        authority.substring(1, endBracket)
    } else {
        val colonCount = authority.count { it == ':' }
        if (colonCount > 1) {
            return null
        }
        if (colonCount == 1) {
            val separator = authority.indexOf(':')
            val remainder = authority.substring(separator)
            if (!isValidPortSuffix(remainder)) {
                return null
            }
            authority.substring(0, separator)
        } else {
            authority
        }
    }

    if (hostPart.isBlank() || hostPart.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' }) {
        return null
    }

    return HostnameAscii.toAscii(hostPart)
}

private fun isValidPortSuffix(value: String): Boolean {
    return value.length > 1 && value[0] == ':' && value.drop(1).all(Char::isDigit)
}
