package com.handshake.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HnsLocalCertificateRegistryTest {
    @Test
    fun pinnedFingerprintsAreScopedToNormalizedHost() {
        HnsLocalCertificateRegistry.clear()
        val fingerprint = ByteArray(32) { index -> index.toByte() }

        HnsLocalCertificateRegistry.trustHostCertificate("Welcome.", fingerprint)

        assertTrue(HnsLocalCertificateRegistry.hasPinnedFingerprint("welcome", fingerprint))
        assertTrue(HnsLocalCertificateRegistry.hasPinnedFingerprint("WELCOME.", fingerprint))
        assertFalse(HnsLocalCertificateRegistry.hasPinnedFingerprint("other", fingerprint))
        assertFalse(HnsLocalCertificateRegistry.hasPinnedFingerprint("welcome", ByteArray(32)))
    }
}
