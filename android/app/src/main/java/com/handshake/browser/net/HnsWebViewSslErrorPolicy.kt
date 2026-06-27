package com.handshake.browser.net

import android.net.http.SslError
import com.handshake.browser.core.HnsHostPolicy
import java.net.URI
import java.util.Locale

object HnsWebViewSslErrorPolicy {
    fun canProceed(error: SslError): Boolean {
        val url = error.url ?: return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) != "https") {
            return false
        }
        val host = uri.httpAuthorityHost() ?: return false
        if (!HnsHostPolicy.requiresHnsResolution(host)) {
            return false
        }
        val certificate = error.certificate?.getX509Certificate() ?: return false
        return HnsLocalCertificateRegistry.hasPinnedCertificate(host, certificate)
    }
}
