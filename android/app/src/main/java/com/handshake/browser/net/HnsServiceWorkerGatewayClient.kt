package com.handshake.browser.net

import android.webkit.ServiceWorkerClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

class HnsServiceWorkerGatewayClient(
    private val interceptor: HnsWebViewGatewayInterceptor,
) : ServiceWorkerClient() {
    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
        interceptor.intercept(request)
}
