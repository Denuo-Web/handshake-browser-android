package com.handshake.browser.net

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

class HnsProxyController(
    private val context: Context,
) {
    fun applyLoopbackProxy(port: Int, onComplete: (Boolean) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            onComplete(false)
            return
        }

        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule("http://127.0.0.1:$port")
            .build()

        ProxyController.getInstance().setProxyOverride(
            proxyConfig,
            ContextCompat.getMainExecutor(context),
        ) {
            onComplete(true)
        }
    }

    fun clear(onComplete: () -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            onComplete()
            return
        }

        ProxyController.getInstance().clearProxyOverride(
            ContextCompat.getMainExecutor(context),
        ) {
            onComplete()
        }
    }
}
