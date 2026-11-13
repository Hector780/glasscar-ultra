package com.glasscar.launcher

import android.os.Handler
import android.os.Looper
import android.os.Message
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TopwayAudioTest {
    private class Fake : TopwayAudioTransport {
        var receiver: Handler? = null
        var opened = 0
        var queries = 0
        var closed = false
        var openResult = 0
        var setResult = 0
        var deny = false
        val levels = mutableListOf<Int>()
        override fun open(): Int { opened++; if (deny) throw SecurityException(); return openResult }
        override fun addHandler(handler: Handler) { receiver = handler }
        override fun start() = Unit
        override fun query(): Int { queries++; return 0 }
        override fun setLevel(level: Int): Int { levels += level; return setResult }
        override fun close() { closed = true }
        fun emit(level: Int, max: Int, channel: Int = 0x203) {
            receiver!!.dispatchMessage(Message.obtain().apply { what = channel; arg1 = level; arg2 = max })
        }
    }

    @Test fun decodesNativeMaximumAndMuteFlagWithoutConfusingAndroidStream() {
        assertEquals(TopwayVolume(14, 40, false), TopwayVolumeProtocol.decode(14, 40))
        val muted = TopwayVolumeProtocol.decode(Int.MIN_VALUE or 14, Int.MIN_VALUE or 40)!!
        assertEquals(14, muted.level); assertEquals(40, muted.maximum); assertTrue(muted.muted)
        assertEquals(0f, muted.fraction, 0f)
        for ((level, max) in listOf(0 to 0, 42 to 40, 1 to 256, -1 to 40)) {
            assertNull(TopwayVolumeProtocol.decode(level, max))
        }
    }
    @Test fun openingAndPollingNeverSetVolumeAndUnrelatedMessagesDoNotEnableControl() {
        val fake = Fake(); val c = TopwayAudioController({ fake })
        try {
            c.start(true); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, fake.queries); assertTrue(fake.levels.isEmpty())
            fake.emit(10, 40, 0x201)
            assertNull(c.state.volume); assertFalse(c.setFraction(0.5f))
            fake.emit(10, 40)
            assertEquals(0.25f, c.state.volume!!.fraction, 0f)
            assertTrue(fake.levels.isEmpty())
        } finally { c.stop() }
    }
    @Test fun userWriteUsesNativeRangeAndRequiresMatchingReadback() {
        val fake = Fake(); val c = TopwayAudioController({ fake })
        try {
            c.start(true); fake.emit(10, 40)
            assertTrue(c.setFraction(0.3f))
            assertEquals(listOf(12), fake.levels)
            assertEquals(10, c.state.volume!!.level)
            assertEquals(12, c.state.pendingLevel); assertNull(c.state.confirmedLevel)
            fake.emit(11, 40)
            assertEquals(12, c.state.pendingLevel)
            fake.emit(12, 40)
            assertNull(c.state.pendingLevel); assertEquals(12, c.state.confirmedLevel)
            assertFalse(c.state.writeBlocked)
        } finally { c.stop() }
    }
    @Test fun missingReadbackBlocksFurtherWritesAndKeepsObservedLevel() {
        val fake = Fake(); val c = TopwayAudioController({ fake })
        try {
            c.start(true); fake.emit(5, 30); c.setFraction(0.2f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100))
            assertTrue(c.state.writeBlocked); assertFalse(c.setFraction(0.7f))
            assertEquals(5, c.state.volume!!.level); assertEquals(listOf(6), fake.levels)
        } finally { c.stop() }
    }
    @Test fun staleOrInvalidReadsAndNonFiniteInputCannotWrite() {
        var now = 100L
        val fake = Fake(); val c = TopwayAudioController({ fake }, { now })
        try {
            c.start(true); fake.emit(5, 30)
            assertFalse(c.setFraction(Float.NaN)); assertFalse(c.setFraction(Float.POSITIVE_INFINITY))
            now = 6000
            assertFalse(c.setFraction(0.7f))
            fake.emit(50, 30); assertNull(c.state.volume)
            assertFalse(c.setFraction(0.7f)); assertTrue(fake.levels.isEmpty())
        } finally { c.stop() }
    }
    @Test fun permissionFailureAndRejectedOpenRemainUnavailable() {
        for (fake in listOf(Fake().apply { deny = true }, Fake().apply { openResult = -1 })) {
            val c = TopwayAudioController({ fake })
            c.start(true)
            assertTrue(fake.closed); assertNull(c.state.volume); assertFalse(c.setFraction(0.5f))
            assertTrue(c.state.status.startsWith("TW no disponible:")); c.stop()
        }
    }
    @Test fun stoppingRejectsLateCallbacksAndCancelsPollingAcrossResume() {
        val fake = Fake(); val c = TopwayAudioController({ fake })
        c.start(true); fake.emit(5, 30)
        val oldReceiver = fake.receiver!!
        c.stop(); val queriesBefore = fake.queries
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(queriesBefore, fake.queries); assertNull(c.state.volume)
        c.start(true)
        oldReceiver.dispatchMessage(Message.obtain().apply { what = 0x203; arg1 = 29; arg2 = 30 })
        assertNull(c.state.volume)
        fake.emit(6, 30); assertEquals(6, c.state.volume!!.level); c.stop()
    }
    @Test fun mcuNightChannelIsReadWithoutTouchingVolume() {
        val fake = Fake(); val c = TopwayAudioController({ fake })
        try {
            c.start(true); shadowOf(Looper.getMainLooper()).idle()
            assertNull(c.state.nightLights)
            fake.emit(1, 0x0503, TopwayVolumeProtocol.LIGHT_CHANNEL)
            assertEquals(true, c.state.nightLights); assertNull(c.state.volume)
            fake.emit(0, 0x0503, TopwayVolumeProtocol.LIGHT_CHANNEL)
            assertEquals(false, c.state.nightLights)
            fake.emit(-1, 0, TopwayVolumeProtocol.LIGHT_CHANNEL)
            assertNull(c.state.nightLights)
            assertTrue(fake.levels.isEmpty())
        } finally { c.stop() }
        assertNull(c.state.nightLights)
    }
    @Test fun unsupportedDeviceNeverLoadsVendorFramework() {
        val c = TopwayAudioController({ error("must not load TW") })
        c.start(false); assertNull(c.state.volume); assertFalse(c.setFraction(0.5f)); c.stop()
    }
    @Test fun rejectedWriteDisablesControlWithoutOptimisticVolumeChange() {
        val fake = Fake().apply { setResult = -1 }; val c = TopwayAudioController({ fake })
        try {
            c.start(true); fake.emit(3, 40)
            assertFalse(c.setFraction(0.1f)); assertTrue(c.state.writeBlocked)
            assertEquals(3, c.state.volume!!.level); assertNull(c.state.confirmedLevel)
        } finally { c.stop() }
    }
}
