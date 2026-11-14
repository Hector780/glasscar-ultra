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

/** 4.7: TS10 backlight through TW 516/258. A simulated MCU validates the contract, not the physical panel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TopwayBrightnessTest {
    private class Fake : TopwayAudioTransport {
        var receiver: Handler? = null
        var modeQueries = 0
        var lightQueries = 0
        var backlightResult = 0
        val backlight = mutableListOf<Pair<Int, Int>>()
        val volumeWrites = mutableListOf<Int>()
        override fun open() = 0
        override fun addHandler(handler: Handler) { receiver = handler }
        override fun start() = Unit
        override fun query() = 0
        override fun setLevel(level: Int): Int { volumeWrites.add(level); return 0 }
        override fun queryLights(): Int { lightQueries++; return 0 }
        override fun queryBacklightMode(): Int { modeQueries++; return 0 }
        override fun setBacklight(night: Int, level: Int): Int { backlight.add(night to level); return backlightResult }
        override fun close() = Unit
        fun emit(channel: Int, arg1: Int, arg2: Int) {
            receiver!!.dispatchMessage(Message.obtain().apply { what = channel; this.arg1 = arg1; this.arg2 = arg2 })
        }
        /** 516: arg1 = night flag, arg2 = day or (night shl 8), exactly as TWService and SystemUI decode it. */
        fun light(night: Int, day: Int, nightLevel: Int) = emit(TopwayBrightnessProtocol.CHANNEL, night, day or (nightLevel shl 8))
        fun mode(value: Int) = emit(TopwayBrightnessProtocol.MODE_CHANNEL, 0, value)
    }

    private fun started(fake: Fake, clock: () -> Long = { 100L }): TopwayAudioController {
        val controller = TopwayAudioController({ fake }, clock)
        controller.start(true)
        shadowOf(Looper.getMainLooper()).idle()
        return controller
    }

    @Test fun decodesBothLevelsWithTheNativeMaximumOfTenAndRejectsAnythingElse() {
        assertEquals(10, TopwayBrightnessProtocol.MAXIMUM)
        assertEquals(TopwayBacklight(true, 3, 5), TopwayBrightnessProtocol.decode(1, 0x0503))
        assertEquals(TopwayBacklight(false, 10, 0), TopwayBrightnessProtocol.decode(0, 10))
        for ((arg1, arg2) in listOf(2 to 0x0503, -1 to 0, 0 to 11, 0 to 0x0b03, 1 to 255)) {
            assertNull(TopwayBrightnessProtocol.decode(arg1, arg2))
        }
        for (raw in listOf(0, 1, 2)) assertEquals(raw, TopwayBrightnessProtocol.mode(raw))
        for (raw in listOf(-1, 3, 255)) assertNull(TopwayBrightnessProtocol.mode(raw))
    }

    @Test fun modeTableMatchesTheNativePanelAndTargetsNeverReachZero() {
        // blmode 0 follows the night flag; 1 is day and 2 is night whatever the flag says; unknown is not guessed.
        assertEquals(true, TopwayBrightnessProtocol.activeNight(0, true))
        assertEquals(false, TopwayBrightnessProtocol.activeNight(0, false))
        assertEquals(false, TopwayBrightnessProtocol.activeNight(1, true))
        assertEquals(true, TopwayBrightnessProtocol.activeNight(2, false))
        assertNull(TopwayBrightnessProtocol.activeNight(null, true))
        assertNull(TopwayBrightnessProtocol.activeNight(7, false))
        assertEquals(1, TopwayBrightnessProtocol.target(0f))
        assertEquals(1, TopwayBrightnessProtocol.target(0.04f))
        assertEquals(4, TopwayBrightnessProtocol.target(0.44f))
        assertEquals(10, TopwayBrightnessProtocol.target(1f))
        assertEquals(10, TopwayBrightnessProtocol.target(3f))
    }

    @Test fun nothingIsWrittenWithoutAFreshReadingAndAKnownMode() {
        val fake = Fake(); val c = started(fake)
        try {
            assertTrue(c.state.connected)
            assertFalse(c.setBrightnessFraction(0.5f))
            fake.light(0, 3, 5)
            // The level is known but blmode is not: GlassCar does not guess which level governs the panel.
            assertNull(c.state.brightness.level); assertFalse(c.setBrightnessFraction(0.5f))
            fake.mode(9)
            assertNull(c.state.brightness.mode); assertFalse(c.setBrightnessFraction(0.5f))
            fake.mode(0)
            assertEquals(3, c.state.brightness.level)
            assertFalse(c.setBrightnessFraction(Float.NaN))
            assertTrue(fake.backlight.isEmpty()); assertTrue(fake.volumeWrites.isEmpty())
        } finally { c.stop() }
        assertFalse(c.state.connected); assertNull(c.state.brightness.reading)
    }

    @Test fun userChangeWritesTheDaySlotAndNeedsTheStereoToConfirm() {
        val fake = Fake(); val c = started(fake)
        try {
            fake.mode(0); fake.light(0, 3, 5)
            assertTrue(c.setBrightnessFraction(0.7f))
            assertEquals(listOf(0 to 7), fake.backlight)
            val waiting = c.state.brightness
            assertEquals(7, waiting.pending); assertEquals(7, waiting.shownLevel); assertEquals(3, waiting.level)
            assertNull(waiting.confirmed); assertEquals(0, waiting.lastWriteResult)
            // A poll answer that still carries the old level does not confirm anything.
            fake.light(0, 3, 5)
            assertEquals(7, c.state.brightness.pending)
            fake.light(0, 7, 5)
            val done = c.state.brightness
            assertNull(done.pending); assertEquals(7, done.confirmed); assertEquals(7, done.level)
            assertEquals("Brillo confirmado por el estéreo", done.status)
            // Same level again, or the same request while it is pending, is not a second command.
            assertTrue(c.setBrightnessFraction(0.7f))
            assertTrue(c.setBrightnessFraction(0.2f)); assertTrue(c.setBrightnessFraction(0.2f))
            assertEquals(listOf(0 to 7, 0 to 2), fake.backlight)
            assertTrue(fake.volumeWrites.isEmpty())
        } finally { c.stop() }
    }

    @Test fun nightSlotIsUsedWhenNightGovernsAndZeroIsNeverSent() {
        val fake = Fake(); val c = started(fake)
        try {
            // Automatic mode with the lights on.
            fake.mode(0); fake.light(1, 8, 4)
            assertEquals(true, c.state.brightness.activeNight); assertEquals(4, c.state.brightness.level)
            assertTrue(c.setBrightnessFraction(0f))
            assertEquals(listOf(1 to 1), fake.backlight)
            // The day level changing meanwhile is not the confirmation of a night write.
            fake.light(1, 1, 4)
            assertEquals(1, c.state.brightness.pending)
            fake.light(1, 1, 1)
            assertEquals(1, c.state.brightness.confirmed)
            // Forced night (blmode 2) with the lights off still writes the night slot; forced day writes day.
            fake.mode(2); fake.light(0, 8, 1)
            assertTrue(c.setBrightnessFraction(0.6f))
            fake.mode(1); fake.light(1, 8, 6)
            assertEquals(8, c.state.brightness.level)
            assertTrue(c.setBrightnessFraction(0.5f))
            assertEquals(listOf(1 to 1, 1 to 6, 0 to 5), fake.backlight)
        } finally { c.stop() }
    }

    @Test fun unconfirmedOrRejectedChangesFallBackToTheStereoReading() {
        val fake = Fake(); val c = started(fake)
        try {
            fake.mode(0); fake.light(0, 3, 5)
            assertTrue(c.setBrightnessFraction(0.9f))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3_100))
            val silent = c.state.brightness
            assertNull(silent.pending); assertNull(silent.confirmed)
            assertEquals(3, silent.shownLevel); assertEquals(9, silent.requested)
            assertEquals("Brillo sin confirmar por el estéreo", silent.status)
            fake.backlightResult = -1
            fake.light(0, 3, 5)
            assertFalse(c.setBrightnessFraction(0.6f))
            val rejected = c.state.brightness
            assertNull(rejected.pending); assertEquals(-1, rejected.lastWriteResult); assertEquals(3, rejected.shownLevel)
            assertTrue(rejected.status.startsWith("TW rechazó el brillo"))
        } finally { c.stop() }
    }

    @Test fun nativePanelChangesResyncAndStaleOrInvalidReadingsBlockWrites() {
        var now = 100L
        val fake = Fake(); val c = started(fake) { now }
        try {
            fake.mode(0); fake.light(0, 3, 5)
            // The driver moves the stereo's own slider: the shared state follows without any GlassCar write.
            fake.light(0, 8, 5)
            assertEquals(8, c.state.brightness.level); assertNull(c.state.brightness.pending)
            assertEquals(0.8f, brightnessModel(c.state, 0.3f, now).fraction, 0f)
            fake.emit(TopwayBrightnessProtocol.CHANNEL, 0, 0x0b0b)
            assertNull(c.state.brightness.reading); assertFalse(c.setBrightnessFraction(0.5f))
            fake.light(0, 8, 5)
            now = 6_000L
            assertFalse(c.state.brightness.usable(now)); assertFalse(c.setBrightnessFraction(0.5f))
            assertTrue(fake.backlight.isEmpty())
        } finally { c.stop() }
    }

    @Test fun lightsKeepWorkingAndTheModeIsAskedAtStartAndEveryFifthPoll() {
        val fake = Fake(); val c = started(fake)
        try {
            assertEquals(1, fake.modeQueries); assertEquals(1, fake.lightQueries)
            fake.light(1, 3, 5)
            assertEquals(true, c.state.nightLights)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8_500))
            assertEquals(5, fake.lightQueries); assertEquals(1, fake.modeQueries)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_000))
            assertEquals(6, fake.lightQueries); assertEquals(2, fake.modeQueries)
        } finally { c.stop() }
    }

    @Test fun oneModelFeedsEveryControlAndTheReportKeepsTheEvidence() {
        val off = brightnessModel(TopwayAudioState(), 0.4f, 0L)
        assertFalse(off.native); assertFalse(off.ready); assertEquals(0.4f, off.fraction, 0f); assertNull(off.level)
        val fake = Fake(); val c = started(fake)
        try {
            val waiting = brightnessModel(c.state, 0.4f, 100L)
            // Connected TS10 without a reading: native but not ready, and the Android value is not shown as if real.
            assertTrue(waiting.native); assertFalse(waiting.ready); assertEquals(0f, waiting.fraction, 0f)
            fake.mode(0); fake.light(0, 3, 5)
            val ready = brightnessModel(c.state, 0.4f, 100L)
            assertTrue(ready.ready); assertEquals(0.3f, ready.fraction, 0f); assertEquals(3, ready.level); assertEquals(10, ready.maximum)
            c.setBrightnessFraction(0.7f)
            assertEquals(0.7f, brightnessModel(c.state, 0.4f, 100L).fraction, 0f)
            val text = TopwayAudio.brightnessReport(c.state.brightness, 100L)
            assertTrue(text, text.contains("día: 3 · noche: 5 · gobierna: día · blmode (258): 0 automático"))
            assertTrue(text, text.contains("solicitado: 7 (día) · pendiente: 7 · confirmado: — · retorno: 0"))
        } finally { c.stop() }
    }
}
