package com.handshake.browser.net

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

interface HnsConnectTerminator {
    @Throws(IOException::class)
    fun prepare(target: ConnectTarget) {
    }

    @Throws(IOException::class)
    fun secure(client: Socket, target: ConnectTarget): Socket
}

class LocalTlsHnsConnectTerminator(
    private val certificateProvider: LocalTlsCertificateProvider = NativeBridge,
    private val certificateRegistry: HnsLocalCertificateRegistry = HnsLocalCertificateRegistry,
) : HnsConnectTerminator {
    private val contexts = ConcurrentHashMap<String, SSLContext>()

    override fun prepare(target: ConnectTarget) {
        contextFor(target.host)
    }

    override fun secure(client: Socket, target: ConnectTarget): Socket {
        val context = contextFor(target.host)
        val sslSocket = context.socketFactory.createSocket(client, target.host, target.port, false) as SSLSocket
        sslSocket.useClientMode = false
        sslSocket.soTimeout = client.soTimeout
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun contextFor(host: String): SSLContext {
        val normalizedHost = host.trim().trimEnd('.').lowercase(Locale.US)
        return try {
            contexts.computeIfAbsent(normalizedHost) { buildContext(it) }
        } catch (error: Exception) {
            throw IOException("local HNS TLS setup failed", error)
        }
    }

    private fun buildContext(host: String): SSLContext {
        val localCertificate = certificateProvider.localTlsCertificate(host)
            ?: throw IllegalStateException("local HNS TLS certificate unavailable")
        val certificate = decodeCertificate(localCertificate.certificateDer)
        val privateKey = decodePrivateKey(localCertificate.privateKeyPkcs8Der)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry(KEY_ALIAS, privateKey, KEY_PASSWORD, arrayOf(certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            init(keyStore, KEY_PASSWORD)
            keyManagers
        }
        certificateRegistry.trustHostCertificate(host, localCertificate.certificateSha256)
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, null, SecureRandom())
        }
    }

    private fun decodeCertificate(certificateDer: ByteArray): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return certificateFactory.generateCertificate(ByteArrayInputStream(certificateDer)) as X509Certificate
    }

    private fun decodePrivateKey(privateKeyDer: ByteArray): PrivateKey {
        val keySpec = PKCS8EncodedKeySpec(privateKeyDer)
        return KEY_ALGORITHMS.firstNotNullOfOrNull { algorithm ->
            runCatching { KeyFactory.getInstance(algorithm).generatePrivate(keySpec) }.getOrNull()
        } ?: throw IllegalStateException("unsupported local HNS TLS private key")
    }

    private companion object {
        const val KEY_ALIAS = "hns-connect"
        val KEY_PASSWORD = charArrayOf()
        val KEY_ALGORITHMS = listOf("EC", "RSA", "Ed25519")
    }
}
