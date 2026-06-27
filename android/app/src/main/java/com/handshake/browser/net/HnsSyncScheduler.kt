package com.handshake.browser.net

import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class HnsSyncSnapshot(
    val statusJson: String,
    val updatedAtMillis: Long,
)

class HnsSyncScheduler(
    private val dataDir: File,
    private val bridge: HnsSyncBridge = NativeBridge,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var future: ScheduledFuture<*>? = null

    @Volatile
    var lastSnapshot: HnsSyncSnapshot? = null
        private set

    fun start(onSnapshot: (HnsSyncSnapshot) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            return
        }

        future = executor.scheduleWithFixedDelay(
            { tick(onSnapshot) },
            0,
            intervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    internal fun tick(onSnapshot: (HnsSyncSnapshot) -> Unit) {
        if (!running.get()) {
            return
        }

        runOnce(onSnapshot)
    }

    internal fun runOnce(onSnapshot: (HnsSyncSnapshot) -> Unit) {
        val snapshot = HnsSyncSnapshot(
            statusJson = bridge.syncOnce(dataDir.absolutePath),
            updatedAtMillis = clock(),
        )
        lastSnapshot = snapshot
        onSnapshot(snapshot)
    }

    override fun close() {
        running.set(false)
        future?.cancel(true)
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 60_000
    }
}
