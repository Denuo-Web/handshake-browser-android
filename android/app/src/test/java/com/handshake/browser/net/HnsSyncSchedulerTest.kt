package com.handshake.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class HnsSyncSchedulerTest {
    @Test
    fun runOncePublishesNativeSyncSnapshot() {
        val dataDir = File("/tmp/hns-browser-test")
        val bridge = RecordingSyncBridge(
            """{"status":"idle","attempted":0,"successful":0,"accepted":0,"peerCount":0,"peerGroups":0,"bestHeight":0,"bestPeerHeight":null,"resourceCacheEntries":0,"resourceCacheBytes":0,"resourceCacheEvicted":0,"error":null}""",
        )
        val scheduler = HnsSyncScheduler(
            dataDir = dataDir,
            bridge = bridge,
            clock = { 1234L },
        )
        var snapshot: HnsSyncSnapshot? = null

        scheduler.runOnce { snapshot = it }

        assertEquals(dataDir.absolutePath, bridge.paths.single())
        assertEquals(1234L, snapshot?.updatedAtMillis)
        assertEquals(bridge.response, snapshot?.statusJson)
        assertSame(snapshot, scheduler.lastSnapshot)
        scheduler.close()
    }

    private class RecordingSyncBridge(
        val response: String,
    ) : HnsSyncBridge {
        val paths = mutableListOf<String>()

        override fun syncOnce(dataDir: String): String {
            paths += dataDir
            return response
        }
    }
}
