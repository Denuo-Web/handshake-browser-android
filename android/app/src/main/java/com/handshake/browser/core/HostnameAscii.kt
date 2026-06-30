package com.handshake.browser.core

import java.net.IDN
import java.util.Locale

internal object HostnameAscii {
    fun toAscii(host: String): String? {
        val normalized = host.removeSurrounding("[", "]")
        if (normalized.isBlank() || normalized.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' }) {
            return null
        }

        runCatching { IDN.toASCII(normalized).lowercase(Locale.US) }
            .getOrNull()
            ?.let { return it }

        val labels = normalized
            .replace('\u3002', '.')
            .replace('\uFF0E', '.')
            .replace('\uFF61', '.')
            .split('.')
        val asciiLabels = labels.map { label ->
            toAsciiLabel(label) ?: return null
        }
        return asciiLabels.joinToString(".").lowercase(Locale.US)
    }

    private fun toAsciiLabel(label: String): String? {
        if (label.isEmpty()) {
            return null
        }
        if (label.all { it.code < ASCII_LIMIT }) {
            return label.takeIf { it.all(::isAsciiHostChar) }
        }

        return punycodeEncode(label)?.let { "xn--$it" }
    }

    private fun punycodeEncode(label: String): String? {
        val codePoints = label.codePoints().toArray()
        if (codePoints.isEmpty() || codePoints.any { it in SURROGATE_MIN..SURROGATE_MAX || it > UNICODE_MAX }) {
            return null
        }

        val output = StringBuilder()
        for (codePoint in codePoints) {
            if (codePoint < ASCII_LIMIT) {
                val char = codePoint.toChar()
                if (!isAsciiHostChar(char)) {
                    return null
                }
                output.append(char.lowercaseChar())
            }
        }

        val basicLength = output.length
        var handled = basicLength
        if (basicLength > 0 && handled < codePoints.size) {
            output.append(DELIMITER)
        }

        var n = INITIAL_N
        var delta = 0L
        var bias = INITIAL_BIAS
        while (handled < codePoints.size) {
            val next = codePoints.filter { it >= n }.minOrNull() ?: return null
            val handledPlusOne = handled + 1
            delta += (next - n).toLong() * handledPlusOne
            n = next

            for (codePoint in codePoints) {
                if (codePoint < n) {
                    delta += 1
                }
                if (codePoint == n) {
                    var q = delta
                    var k = BASE
                    while (true) {
                        val threshold = when {
                            k <= bias + TMIN -> TMIN
                            k >= bias + TMAX -> TMAX
                            else -> k - bias
                        }
                        if (q < threshold) {
                            break
                        }
                        val digit = threshold + ((q - threshold) % (BASE - threshold)).toInt()
                        output.append(encodeDigit(digit))
                        q = (q - threshold) / (BASE - threshold)
                        k += BASE
                    }
                    output.append(encodeDigit(q.toInt()))
                    bias = adapt(delta, handledPlusOne, handled == basicLength)
                    delta = 0
                    handled += 1
                }
            }

            delta += 1
            n += 1
        }

        return output.toString()
    }

    private fun adapt(deltaValue: Long, numPoints: Int, firstTime: Boolean): Int {
        var delta = if (firstTime) deltaValue / DAMP else deltaValue / 2
        delta += delta / numPoints

        var k = 0
        while (delta > ((BASE - TMIN) * TMAX) / 2) {
            delta /= BASE - TMIN
            k += BASE
        }

        return (k + (((BASE - TMIN + 1) * delta) / (delta + SKEW))).toInt()
    }

    private fun encodeDigit(digit: Int): Char =
        when (digit) {
            in 0..25 -> ('a'.code + digit).toChar()
            in 26..35 -> ('0'.code + digit - 26).toChar()
            else -> error("invalid punycode digit")
        }

    private fun isAsciiHostChar(char: Char): Boolean =
        char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '-'

    private const val ASCII_LIMIT = 0x80
    private const val UNICODE_MAX = 0x10FFFF
    private const val SURROGATE_MIN = 0xD800
    private const val SURROGATE_MAX = 0xDFFF
    private const val BASE = 36
    private const val TMIN = 1
    private const val TMAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128
    private const val DELIMITER = '-'
}
