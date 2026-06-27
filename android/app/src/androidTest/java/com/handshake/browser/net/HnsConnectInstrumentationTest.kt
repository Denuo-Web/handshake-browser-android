package com.handshake.browser.net

import android.net.http.SslError
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@RunWith(AndroidJUnit4::class)
class HnsConnectInstrumentationTest {
    @Test
    fun hnsConnectUsesNativeLocalTlsAndForwardsPostBody() {
        val host = "connecttest"
        val responseBytes = "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1)
        val bridge = RecordingGatewayBridge(responseBytes)
        val dataDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir

        LoopbackProxyServer(0, dataDir = dataDir, hnsGatewayBridge = bridge).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.getOutputStream().write(
                    "CONNECT $host:443 HTTP/1.1\r\nHost: $host:443\r\n\r\n"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()
                val connectResponse = readHeaders(socket.getInputStream())
                assertTrue(connectResponse.startsWith("HTTP/1.1 200 Connection Established\r\n"))

                val tlsSocket = trustAllSslContext().socketFactory
                    .createSocket(socket, host, 443, false) as SSLSocket
                tlsSocket.use {
                    it.soTimeout = SOCKET_TIMEOUT_MS
                    it.useClientMode = true
                    it.startHandshake()

                    val certificate = it.session.peerCertificates.single() as X509Certificate
                    assertTrue(HnsLocalCertificateRegistry.hasPinnedCertificate(host, certificate))
                    assertTrue(
                        HnsWebViewSslErrorPolicy.canProceed(
                            SslError(SslError.SSL_UNTRUSTED, certificate, "https://$host/form"),
                        ),
                    )
                    assertFalse(
                        HnsWebViewSslErrorPolicy.canProceed(
                            SslError(SslError.SSL_UNTRUSTED, certificate, "https://example.com/form"),
                        ),
                    )

                    it.getOutputStream().write(
                        (
                            "POST /form?q=1 HTTP/1.1\r\n" +
                                "Host: $host\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: 7\r\n\r\n" +
                                "payload"
                            ).toByteArray(StandardCharsets.ISO_8859_1),
                    )
                    it.getOutputStream().flush()

                    val responseHeaders = readHeaders(it.getInputStream())
                    assertTrue(responseHeaders.startsWith("HTTP/1.1 204 No Content\r\n"))
                }
            }
        }

        assertEquals(
            GatewayCall(
                dataDir.absolutePath,
                "POST",
                "https",
                host,
                443,
                "/form?q=1",
                listOf(
                    "Host" to host,
                    "Content-Type" to "text/plain",
                    "Content-Length" to "7",
                ),
                "payload",
            ),
            bridge.calls.single(),
        )
    }

    private data class GatewayCall(
        val dataDir: String,
        val method: String,
        val scheme: String,
        val host: String,
        val port: Int,
        val pathAndQuery: String,
        val headers: List<Pair<String, String>>,
        val body: String,
    )

    private class RecordingGatewayBridge(
        private val response: ByteArray,
    ) : HnsGatewayBridge {
        val calls = Collections.synchronizedList(mutableListOf<GatewayCall>())

        override fun httpResponse(
            dataDir: String,
            method: String,
            scheme: String,
            host: String,
            port: Int,
            pathAndQuery: String,
            headers: List<Pair<String, String>>,
            body: ByteArray,
        ): ByteArray {
            calls += GatewayCall(
                dataDir,
                method,
                scheme,
                host,
                port,
                pathAndQuery,
                headers,
                body.toString(StandardCharsets.ISO_8859_1),
            )
            return response
        }
    }

    private fun readHeaders(input: InputStream): String {
        val output = ByteArrayOutputStream()
        var matched = 0
        while (matched < HEADER_END.size) {
            val next = input.read()
            require(next >= 0) { "unexpected end of stream" }
            output.write(next)
            matched = if (next.toByte() == HEADER_END[matched]) matched + 1 else 0
        }
        return output.toByteArray().toString(StandardCharsets.ISO_8859_1)
    }

    private fun trustAllSslContext(): SSLContext {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )
        return SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 10_000
        val HEADER_END = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
    }
}
