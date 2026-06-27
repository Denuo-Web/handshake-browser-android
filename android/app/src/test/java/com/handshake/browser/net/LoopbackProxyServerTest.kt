package com.handshake.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

class LoopbackProxyServerTest {
    @Test
    fun parsesConnectRequestLine() {
        val line = ProxyRequestLine.parse("CONNECT example.com:443 HTTP/1.1")

        assertEquals("CONNECT", line.method)
        assertEquals("example.com:443", line.target)
        assertEquals("HTTP/1.1", line.version)
    }

    @Test
    fun parsesConnectAuthority() {
        assertEquals(ConnectTarget("example.com", 443), ConnectTarget.parse("example.com:443"))
        assertEquals(ConnectTarget("::1", 8443), ConnectTarget.parse("[::1]:8443"))
    }

    @Test
    fun parsesAbsoluteHttpTarget() {
        val target = ProxyRequestLine.parse("GET http://example.com/path?q=1 HTTP/1.1").toHttpTarget()

        assertEquals("http", target.scheme)
        assertEquals("example.com", target.host)
        assertEquals(80, target.port)
        assertEquals("/path?q=1", target.pathAndQuery)
    }

    @Test
    fun parsesDottedHnsAbsoluteHttpTarget() {
        val target = ProxyRequestLine.parse("GET https://welcome.2d/path?q=1 HTTP/1.1").toHttpTarget()

        assertEquals("https", target.scheme)
        assertEquals("welcome.2d", target.host)
        assertEquals(443, target.port)
        assertEquals("/path?q=1", target.pathAndQuery)
    }

    @Test
    fun parsesWebSocketAbsoluteHttpTarget() {
        val target = ProxyRequestLine.parse("GET wss://welcome/socket HTTP/1.1").toHttpTarget()

        assertEquals("wss", target.scheme)
        assertEquals("welcome", target.host)
        assertEquals(443, target.port)
        assertEquals("/socket", target.pathAndQuery)
    }

    @Test
    fun rewritesAbsoluteFormToOriginForm() {
        val request = ProxyRequest(
            line = ProxyRequestLine.parse("GET http://example.com/path?q=1 HTTP/1.1"),
            headers = listOf(
                "Host" to "example.com",
                "Proxy-Connection" to "keep-alive",
                "User-Agent" to "test",
            ),
        )
        val bytes = request.toOriginBytes(request.line.toHttpTarget())
        val rewritten = bytes.toString(Charsets.ISO_8859_1)

        assertEquals("GET /path?q=1 HTTP/1.1", rewritten.lineSequence().first())
        assertFalse(rewritten.contains("Proxy-Connection"))
        assertTrue(rewritten.contains("Connection: close"))
    }

    @Test
    fun hnsSingleLabelRequiresLocalResolution() {
        assertTrue(requiresHnsResolution("welcome"))
        assertTrue(requiresHnsResolution("name."))
    }

    @Test
    fun dottedHnsHostRequiresLocalResolutionWhenTldIsNotCommonIcann() {
        assertTrue(requiresHnsResolution("welcome.2d"))
        assertTrue(requiresHnsResolution("blog.proofofconcept"))
    }

    @Test
    fun icannLocalhostAndIpHostsDoNotRequireHnsResolution() {
        assertFalse(requiresHnsResolution("example.com"))
        assertFalse(requiresHnsResolution("handshake.org"))
        assertFalse(requiresHnsResolution("example.io"))
        assertFalse(requiresHnsResolution("localhost"))
        assertFalse(requiresHnsResolution("example"))
        assertFalse(requiresHnsResolution("invalid"))
        assertFalse(requiresHnsResolution("local"))
        assertFalse(requiresHnsResolution("test"))
        assertFalse(requiresHnsResolution("127.0.0.1"))
        assertFalse(requiresHnsResolution("[::1]"))
    }

    @Test
    fun hnsHttpRequestUsesNativeGatewayBridge() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 503 HNS Resolution Unavailable\r\nContent-Length: 4\r\nConnection: close\r\n\r\ntest"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-test").toFile()
        LoopbackProxyServer(0, dataDir = dataDir, hnsGatewayBridge = bridge).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    "POST http://welcome/path?q=1 HTTP/1.1\r\nHost: welcome\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nhi"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 503 HNS Resolution Unavailable\r\n"))
            }
        }

        assertEquals(
            GatewayCall(
                dataDir.absolutePath,
                "POST",
                "http",
                "welcome",
                80,
                "/path?q=1",
                listOf("Host" to "welcome", "Content-Type" to "text/plain", "Content-Length" to "2"),
                "hi",
            ),
            bridge.calls.single(),
        )
        dataDir.deleteRecursively()
    }

    @Test
    fun hnsConnectFailsClosedBeforeNativeOrSystemResolution() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-connect-test").toFile()
        LoopbackProxyServer(
            0,
            dataDir = dataDir,
            hnsGatewayBridge = bridge,
            hnsConnectTerminator = UnavailableConnectTerminator,
        ).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    "CONNECT welcome:443 HTTP/1.1\r\nHost: welcome:443\r\n\r\n"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 501 HNS HTTPS Termination Unavailable\r\n"))
            }
        }

        assertTrue(bridge.calls.isEmpty())
        dataDir.deleteRecursively()
    }

    @Test
    fun hnsConnectTerminatesToNativeGatewayWithRequestBody() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 201 Created\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-connect-body-test").toFile()
        LoopbackProxyServer(
            0,
            dataDir = dataDir,
            hnsGatewayBridge = bridge,
            hnsConnectTerminator = PassthroughConnectTerminator,
        ).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    (
                        "CONNECT welcome:443 HTTP/1.1\r\nHost: welcome:443\r\n\r\n" +
                            "POST /submit?q=1 HTTP/1.1\r\nHost: welcome\r\nContent-Length: 2\r\n\r\nhi"
                        ).toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 200 Connection Established\r\n"))
                assertTrue(response.contains("HTTP/1.1 201 Created\r\n"))
            }
        }

        assertEquals(
            GatewayCall(
                dataDir.absolutePath,
                "POST",
                "https",
                "welcome",
                443,
                "/submit?q=1",
                listOf("Host" to "welcome", "Content-Length" to "2"),
                "hi",
            ),
            bridge.calls.single(),
        )
        dataDir.deleteRecursively()
    }

    @Test
    fun hnsConnectRejectsTunneledHostMismatchBeforeNativeGateway() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-connect-mismatch-test").toFile()
        LoopbackProxyServer(
            0,
            dataDir = dataDir,
            hnsGatewayBridge = bridge,
            hnsConnectTerminator = PassthroughConnectTerminator,
        ).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    (
                        "CONNECT welcome:443 HTTP/1.1\r\nHost: welcome:443\r\n\r\n" +
                            "GET https://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n"
                        ).toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 200 Connection Established\r\n"))
                assertTrue(response.contains("HTTP/1.1 400 HNS Request Mismatch\r\n"))
            }
        }

        assertTrue(bridge.calls.isEmpty())
        dataDir.deleteRecursively()
    }

    @Test
    fun hnsConnectWebSocketUpgradeFailsClosedBeforeNativeGateway() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-connect-websocket-test").toFile()
        LoopbackProxyServer(
            0,
            dataDir = dataDir,
            hnsGatewayBridge = bridge,
            hnsConnectTerminator = PassthroughConnectTerminator,
        ).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    (
                        "CONNECT welcome:443 HTTP/1.1\r\nHost: welcome:443\r\n\r\n" +
                            "GET /socket HTTP/1.1\r\nHost: welcome\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: test\r\nSec-WebSocket-Version: 13\r\n\r\n"
                        ).toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 200 Connection Established\r\n"))
                assertTrue(response.contains("HTTP/1.1 501 HNS Protocol Upgrade Unsupported\r\n"))
            }
        }

        assertTrue(bridge.calls.isEmpty())
        dataDir.deleteRecursively()
    }

    @Test
    fun hnsPlainWebSocketUpgradeFailsClosedBeforeNativeGateway() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-plain-websocket-test").toFile()
        LoopbackProxyServer(0, dataDir = dataDir, hnsGatewayBridge = bridge).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    "GET ws://welcome/socket HTTP/1.1\r\nHost: welcome\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: test\r\nSec-WebSocket-Version: 13\r\n\r\n"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 501 HNS Protocol Upgrade Unsupported\r\n"))
            }
        }

        assertTrue(bridge.calls.isEmpty())
        dataDir.deleteRecursively()
    }

    @Test
    fun icannPlainWebSocketUpgradePreservesUpgradeHeaders() {
        val received = ArrayBlockingQueue<String>(1)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { origin ->
            val originThread = Thread {
                origin.accept().use { socket ->
                    val request = readHeaderText(socket)
                    received.offer(request)
                    socket.getOutputStream().write(
                        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n"
                            .toByteArray(StandardCharsets.ISO_8859_1),
                    )
                    socket.getOutputStream().flush()
                }
            }.apply { start() }

            LoopbackProxyServer(0, hnsGatewayBridge = RecordingGatewayBridge(ByteArray(0))).use { proxy ->
                assertTrue(proxy.start())
                val proxyPort = requireNotNull(proxy.boundPort())

                Socket(InetAddress.getByName("127.0.0.1"), proxyPort).use { socket ->
                    socket.getOutputStream().write(
                        (
                            "GET http://127.0.0.1:${origin.localPort}/socket HTTP/1.1\r\n" +
                                "Host: wrong.example\r\n" +
                                "Upgrade: websocket\r\n" +
                                "Connection: keep-alive, Upgrade\r\n" +
                                "Proxy-Connection: keep-alive\r\n" +
                                "Sec-WebSocket-Key: test\r\n" +
                                "Sec-WebSocket-Version: 13\r\n\r\n"
                            ).toByteArray(StandardCharsets.ISO_8859_1),
                    )
                    socket.getOutputStream().flush()

                    val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                    assertTrue(response.startsWith("HTTP/1.1 101 Switching Protocols\r\n"))
                }
            }
            originThread.join(1_000)
        }

        val request = received.poll(1, TimeUnit.SECONDS).orEmpty()
        assertTrue(request.startsWith("GET /socket HTTP/1.1\r\n"))
        assertTrue(request.contains("Host: 127.0.0.1:"))
        assertTrue(request.contains("Upgrade: websocket\r\n"))
        assertTrue(request.contains("Connection: keep-alive, Upgrade\r\n"))
        assertFalse(request.contains("Proxy-Connection"))
        assertFalse(request.contains("Host: wrong.example"))
    }

    @Test
    fun transferEncodedRequestsFailClosedBeforeNativeGateway() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-transfer-encoding-test").toFile()
        LoopbackProxyServer(0, dataDir = dataDir, hnsGatewayBridge = bridge).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    "POST http://welcome/path HTTP/1.1\r\nHost: welcome\r\nTransfer-Encoding: chunked\r\n\r\n2\r\nhi\r\n0\r\n\r\n"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 501 Transfer Encoding Unsupported\r\n"))
            }
        }

        assertTrue(bridge.calls.isEmpty())
        dataDir.deleteRecursively()
    }

    @Test
    fun conflictingContentLengthFailsClosedBeforeNativeGateway() {
        val bridge = RecordingGatewayBridge(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )
        val dataDir = createTempDirectory("hns-proxy-content-length-test").toFile()
        LoopbackProxyServer(0, dataDir = dataDir, hnsGatewayBridge = bridge).use { proxy ->
            assertTrue(proxy.start())
            val port = requireNotNull(proxy.boundPort())

            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write(
                    "POST http://welcome/path HTTP/1.1\r\nHost: welcome\r\nContent-Length: 2\r\nContent-Length: 3\r\n\r\nhi!"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().readBytes().toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 400 Bad Content-Length\r\n"))
            }
        }

        assertTrue(bridge.calls.isEmpty())
        dataDir.deleteRecursively()
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
        val calls = mutableListOf<GatewayCall>()

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

    private object PassthroughConnectTerminator : HnsConnectTerminator {
        override fun secure(client: Socket, target: ConnectTarget): Socket = client
    }

    private object UnavailableConnectTerminator : HnsConnectTerminator {
        override fun prepare(target: ConnectTarget) {
            throw IOException("unavailable")
        }

        override fun secure(client: Socket, target: ConnectTarget): Socket = client
    }

    private fun readHeaderText(socket: Socket): String {
        val output = StringBuilder()
        val input = socket.getInputStream()
        var matched = 0
        while (matched < HEADER_END.size) {
            val next = input.read()
            if (next < 0) break
            output.append(next.toChar())
            matched = if (next.toByte() == HEADER_END[matched]) matched + 1 else 0
        }
        return output.toString()
    }

    private val HEADER_END = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
}
