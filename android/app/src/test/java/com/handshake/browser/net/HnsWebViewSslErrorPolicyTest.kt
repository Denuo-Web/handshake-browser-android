package com.handshake.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HnsWebViewSslErrorPolicyTest {
    @Test
    fun pinnedLocalCertificatesAreEligibleForWebSocketSslErrors() {
        assertTrue(HnsWebViewSslErrorPolicy.isEligiblePinnedLocalCertificateUrl("wss://welcome/socket"))
    }

    @Test
    fun pinnedLocalCertificatesAreEligibleForHttpsSslErrors() {
        assertTrue(HnsWebViewSslErrorPolicy.isEligiblePinnedLocalCertificateUrl("https://welcome/"))
    }

    @Test
    fun emojiHnsTlsUrlsAreEligibleAfterPunycodeNormalization() {
        assertTrue(HnsWebViewSslErrorPolicy.isEligiblePinnedLocalCertificateUrl("https://🤝/"))
    }

    @Test
    fun pinnedLocalCertificatesRejectNonHnsAndNonTlsUrls() {
        assertFalse(HnsWebViewSslErrorPolicy.isEligiblePinnedLocalCertificateUrl("https://example.com/"))
        assertFalse(HnsWebViewSslErrorPolicy.isEligiblePinnedLocalCertificateUrl("ws://welcome/socket"))
        assertFalse(HnsWebViewSslErrorPolicy.isEligiblePinnedLocalCertificateUrl("not a url"))
    }
}
