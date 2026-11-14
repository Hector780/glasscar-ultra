package com.glasscar.launcher

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ReverseSignalTest {
    @Before fun clean() = ReverseSignal.reset()
    @After fun restore() = ReverseSignal.reset()

    private fun feed(vararg events: Pair<ReverseSource, Int>): ReverseState {
        var state = ReverseState()
        events.forEachIndexed { index, event ->
            state = ReverseProtocol.reduce(state, event.first, event.second, 1_000L * (index + 1))
        }
        return state
    }

    @Test fun onlyZeroAndOneAreReadingsAndChannelsMapWithOrWithoutSignExtension() {
        assertEquals(true, ReverseProtocol.decode(1))
        assertEquals(false, ReverseProtocol.decode(0))
        for (raw in listOf(-1, 2, 255)) assertNull(ReverseProtocol.decode(raw))
        assertEquals(ReverseSource.TW_STATE, ReverseProtocol.source(0x9f1c))
        assertEquals(ReverseSource.TW_STATE, ReverseProtocol.source(0x9f1c.toShort().toInt()))
        assertEquals(ReverseSource.TW_MCU, ReverseProtocol.source(0x20b))
        for (other in listOf(TopwayVolumeProtocol.CHANNEL, TopwayVolumeProtocol.LIGHT_CHANNEL, 0x9f1d, 0)) {
            assertNull(ReverseProtocol.source(other))
        }
    }

    @Test fun risingEdgeCountsOncePerTransitionAndRepeatedReadingsDoNotRetrigger() {
        val state = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_STATE to 1, ReverseSource.TW_BROADCAST to 1)
        assertEquals(true, state.active); assertEquals(1, state.entries); assertEquals(0, state.exits)
        assertEquals(1_000L, state.changedAt); assertEquals(ReverseSource.TW_STATE, state.drivenBy)
        assertEquals(2, state.readings.getValue(ReverseSource.TW_STATE).count)
        val again = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_STATE to 0, ReverseSource.TW_STATE to 1)
        assertEquals(2, again.entries); assertEquals(1, again.exits); assertEquals(true, again.active)
    }

    @Test fun missingOrInvalidReadingsNeverBecomeNotReversing() {
        assertNull(ReverseState().active)
        val unknownToOff = feed(ReverseSource.TW_STATE to 0)
        assertEquals(false, unknownToOff.active); assertEquals(0, unknownToOff.exits); assertEquals(0, unknownToOff.entries)
        val invalid = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_STATE to -1, ReverseSource.TW_STATE to 7)
        assertEquals(true, invalid.active); assertEquals(0, invalid.exits)
        assertEquals(7, invalid.readings.getValue(ReverseSource.TW_STATE).raw)
        assertNull(feed(ReverseSource.TW_BROADCAST to -1).active)
    }

    @Test fun mcuChannelDecidesOnlyUntilAPrimarySourceReports() {
        val mcuOnly = feed(ReverseSource.TW_MCU to 1, ReverseSource.TW_MCU to 0)
        assertEquals(false, mcuOnly.active); assertEquals(1, mcuOnly.entries); assertEquals(1, mcuOnly.exits)
        val mixed = feed(ReverseSource.TW_MCU to 1, ReverseSource.TW_STATE to 1, ReverseSource.TW_MCU to 0,
            ReverseSource.TW_MCU to 1)
        // Once 0x9f1c has spoken, a disagreeing 0x20b is recorded for the diagnostic but cannot create edges.
        assertEquals(true, mixed.active); assertEquals(1, mixed.entries); assertEquals(0, mixed.exits)
        assertEquals(3, mixed.readings.getValue(ReverseSource.TW_MCU).count)
    }

    @Test fun broadcastClosesReverseWhileTheTwConnectionIsPaused() {
        val state = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_BROADCAST to 0)
        assertEquals(false, state.active); assertEquals(1, state.entries); assertEquals(1, state.exits)
        assertEquals(ReverseSource.TW_BROADCAST, state.drivenBy); assertEquals(2_000L, state.changedAt)
    }

    @Test fun sharedSignalKeepsCountersAndReportsRawEvidencePerSource() {
        assertTrue(ReverseSignal.diagnostics(0L).contains("sin lectura"))
        ReverseSignal.report(ReverseSource.TW_STATE, 1, 5_000L)
        ReverseSignal.report(ReverseSource.TW_STATE, 1, 6_000L)
        assertEquals(1, ReverseSignal.state.entries)
        val text = ReverseSignal.diagnostics(9_000L)
        assertTrue(text, text.contains("ACTIVA") && text.contains("entradas: 1"))
        assertTrue(text, text.contains("TW 0x9f1c: crudo=1 · hace 3 s · eventos=2"))
        assertTrue(text, text.contains("TW 0x20b: sin eventos"))
        ReverseSignal.reset()
        assertNull(ReverseSignal.state.active); assertEquals(0, ReverseSignal.state.entries)
    }

    // ---------- Navegación: una vez por transición ----------

    private val idle = ReverseNavigation()

    @Test fun entersCameraWithRearOncePerTransition() {
        val entered = feed(ReverseSource.TW_STATE to 1)
        val step = ReverseNavigator.advance(idle, entered, 0, CameraSource.FRONT)
        assertEquals(ReverseNavigator.CAMERA_PAGE, step.page); assertEquals(CameraSource.REAR, step.source)
        assertTrue(step.entering)
        assertEquals(0, step.navigation.returnPage); assertEquals(CameraSource.FRONT, step.navigation.returnSource)
        // The same state again (recomposition, repeated readings) must not navigate a second time.
        val repeat = ReverseNavigator.advance(step.navigation, entered, 3, CameraSource.REAR)
        assertNull(repeat.page); assertNull(repeat.source); assertFalse(repeat.entering)
        assertEquals(step.navigation, repeat.navigation)
    }

    @Test fun leavingReverseRestoresPreviousPageOnlyIfTheDriverTouchedNothing() {
        val entered = feed(ReverseSource.TW_STATE to 1)
        val left = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_BROADCAST to 0)
        val during = ReverseNavigator.advance(idle, entered, 3, CameraSource.FRONT).navigation
        val untouched = ReverseNavigator.advance(during, left, ReverseNavigator.CAMERA_PAGE, CameraSource.REAR)
        assertEquals(3, untouched.page); assertEquals(CameraSource.FRONT, untouched.source); assertFalse(untouched.entering)
        assertNull(untouched.navigation.returnPage); assertNull(untouched.navigation.returnSource)
        val changedPage = ReverseNavigator.advance(during, left, 1, CameraSource.REAR)
        assertNull(changedPage.page); assertNull(changedPage.source)
        val changedSource = ReverseNavigator.advance(during, left, ReverseNavigator.CAMERA_PAGE, CameraSource.FRONT)
        assertNull(changedSource.page); assertNull(changedSource.source)
    }

    @Test fun alreadyOnCameraKeepsThePageAndReturnsToFrontAfterwards() {
        val entered = feed(ReverseSource.TW_STATE to 1)
        val left = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_STATE to 0)
        val step = ReverseNavigator.advance(idle, entered, ReverseNavigator.CAMERA_PAGE, CameraSource.FRONT)
        assertEquals(ReverseNavigator.CAMERA_PAGE, step.page); assertEquals(CameraSource.REAR, step.source)
        val after = ReverseNavigator.advance(step.navigation, left, ReverseNavigator.CAMERA_PAGE, CameraSource.REAR)
        assertNull(after.page); assertEquals(CameraSource.FRONT, after.source)
    }

    @Test fun edgesHandledBeforeOrWhileHiddenNeverNavigate() {
        val inAndOut = feed(ReverseSource.TW_STATE to 1, ReverseSource.TW_BROADCAST to 0)
        val hidden = ReverseNavigator.advance(idle, inAndOut, 0, CameraSource.FRONT)
        assertNull(hidden.page); assertNull(hidden.source)
        assertEquals(1, hidden.navigation.seenEntries); assertEquals(1, hidden.navigation.seenExits)
        // A launcher composed while reverse is already active starts from the current counters.
        val active = feed(ReverseSource.TW_STATE to 1)
        val startup = ReverseNavigator.advance(ReverseNavigation(active.entries, active.exits), active, 0, CameraSource.FRONT)
        assertNull(startup.page); assertNull(startup.source)
        // No data at all: nothing happens and nothing is invented.
        val none = ReverseNavigator.advance(idle, ReverseState(), 0, CameraSource.FRONT)
        assertNull(none.page); assertNull(none.source); assertEquals(idle, none.navigation)
    }
}
