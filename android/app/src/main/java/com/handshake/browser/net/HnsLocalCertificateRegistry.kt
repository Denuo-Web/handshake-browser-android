package com.handshake.browser.net

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object HnsLocalCertificateRegistry {
    private val pinnedFingerprints = ConcurrentHashMap<String, ByteArray>()

    fun trustHostCertificate(host: String, certificateSha256: ByteArray) {
        pinnedFingerprints[normalizeHost(host)] = certificateSha256.copyOf()
    }

    fun hasPinnedCertificate(host: String, certificate: X509Certificate): Boolean {
        return hasPinnedFingerprint(host, sha256(certificate.encoded))
    }

    internal fun hasPinnedFingerprint(host: String, certificateSha256: ByteArray): Boolean {
        val pinned = pinnedFingerprints[normalizeHost(host)] ?: return false
        return pinned.contentEquals(certificateSha256)
    }

    internal fun clear() {
        pinnedFingerprints.clear()
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun normalizeHost(host: String): String =
        host.trim().trimEnd('.').lowercase(Locale.US)
}
