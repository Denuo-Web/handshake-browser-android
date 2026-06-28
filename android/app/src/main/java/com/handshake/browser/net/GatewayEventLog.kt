package com.handshake.browser.net

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class GatewayEvent(
    val timestampMillis: Long,
    val stage: String,
    val host: String,
    val status: Int,
    val reason: String,
)

internal object GatewayEventLog {
    private const val MAX_EVENTS = 25
    private const val STORE_FILE_NAME = "gateway-events.log"
    private const val FIELD_SEPARATOR = "\t"
    private val events = ArrayDeque<GatewayEvent>()
    private var storeFile: File? = null

    @Synchronized
    fun configureAppStorage(filesDir: File) {
        configure(File(filesDir, STORE_FILE_NAME))
    }

    @Synchronized
    fun configure(file: File?) {
        if (storeFile?.absolutePath == file?.absolutePath) {
            return
        }
        storeFile = file
        events.clear()
        if (file == null || !file.isFile) {
            return
        }
        runCatching {
            file.readLines(StandardCharsets.UTF_8)
                .mapNotNull(::parseEventLine)
                .takeLast(MAX_EVENTS)
                .forEach(events::addLast)
            persistLocked()
        }
    }

    @Synchronized
    fun record(stage: String, host: String, status: Int, reason: String) {
        events.addLast(
            GatewayEvent(
                timestampMillis = System.currentTimeMillis(),
                stage = stage.sanitizeToken(),
                host = host.sanitizeHost(),
                status = status,
                reason = reason.sanitizeReason(),
            ),
        )
        while (events.size > MAX_EVENTS) {
            events.removeFirst()
        }
        persistLocked()
    }

    @Synchronized
    fun snapshot(): List<GatewayEvent> = events.toList()

    @Synchronized
    fun snapshotText(): String {
        if (events.isEmpty()) {
            return "none"
        }
        return events.joinToString(separator = "\n") { event ->
            "${event.timestampMillis} ${event.stage} ${event.host} ${event.status} ${event.reason}"
        }
    }

    @Synchronized
    fun clear() {
        events.clear()
        runCatching { storeFile?.delete() }
    }

    private fun persistLocked() {
        val file = storeFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val contents = if (events.isEmpty()) {
                ""
            } else {
                events.joinToString(separator = "\n", postfix = "\n", transform = ::serializeEvent)
            }
            file.writeText(contents, StandardCharsets.UTF_8)
        }
    }

    private fun serializeEvent(event: GatewayEvent): String =
        listOf(
            event.timestampMillis.toString(),
            event.stage,
            event.host,
            event.status.toString(),
            event.reason,
        ).joinToString(FIELD_SEPARATOR)

    private fun parseEventLine(line: String): GatewayEvent? {
        val parts = line.split(FIELD_SEPARATOR, limit = 5)
        if (parts.size != 5) {
            return null
        }
        return GatewayEvent(
            timestampMillis = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null,
            stage = parts[1].sanitizeToken(),
            host = parts[2].sanitizeHost(),
            status = parts[3].toIntOrNull() ?: return null,
            reason = parts[4].sanitizeReason(),
        )
    }

    private fun String.sanitizeHost(): String {
        return trim()
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .trimEnd('.')
            .lowercase(Locale.US)
            .filter { it.isLetterOrDigit() || it == '-' || it == '.' || it == ':' || it == '[' || it == ']' }
            .take(253)
            .ifBlank { "unknown" }
    }

    private fun String.sanitizeToken(): String {
        return trim()
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .replace(Regex("\\s+"), "_")
            .lowercase(Locale.US)
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(40)
            .ifBlank { "unknown" }
    }

    private fun String.sanitizeReason(): String {
        return trim()
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .replace(Regex("\\s+"), "_")
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(80)
            .ifBlank { "unknown" }
    }
}
