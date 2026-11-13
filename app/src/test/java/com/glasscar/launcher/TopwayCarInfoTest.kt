package com.glasscar.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TopwayCarInfoTest {
    @Test fun gearFollowsAidlTable() {
        assertEquals(Gear.D, TopwayMapping.gear(0))
        assertEquals(Gear.N, TopwayMapping.gear(1))
        assertEquals(Gear.R, TopwayMapping.gear(2))
        assertEquals(Gear.P, TopwayMapping.gear(3))
        assertEquals(Gear.S, TopwayMapping.gear(4))
        assertNull(TopwayMapping.gear(-1))
        assertNull(TopwayMapping.gear(9))
    }

    @Test fun leavingReverseDoesNotInventAGear() {
        assertEquals(Gear.R, TopwayMapping.gearFromReverse(1))
        assertNull(TopwayMapping.gearFromReverse(0))
        assertNull(TopwayMapping.gearFromReverse(-1))
        assertNull(TopwayMapping.gearFromReverse(null))
    }

    @Test fun speedHonoursInvalidAndMiles() {
        assertNull(TopwayMapping.speedKmh(-1, 0))
        assertEquals(50f, TopwayMapping.speedKmh(50, 0)!!, 0.001f)
        assertEquals(80.4672f, TopwayMapping.speedKmh(50, 1)!!, 0.001f)
        assertNull(TopwayMapping.speedKmh(999, 0))
    }

    @Test fun voltageAndFlagsRejectSentinels() {
        assertNull(TopwayMapping.voltage(-1f))
        assertEquals(13.8f, TopwayMapping.voltage(13.8f)!!, 0.001f)
        assertEquals(true, TopwayMapping.flag(1))
        assertEquals(false, TopwayMapping.flag(0))
        assertNull(TopwayMapping.flag(-1))
    }

    @Test fun mergeKeepsFreshValuesAndDropsStaleOnes() {
        val t0 = 10_000L
        val a = mergeCan(CanState(), 40f, Gear.D, null, null, t0)
        assertEquals(40f, a.speedKmh!!, 0f)
        // Llega sólo faros 1 s después: velocidad y marcha siguen vigentes.
        val b = mergeCan(a, null, null, true, null, t0 + 1_000)
        assertEquals(40f, b.speedKmh!!, 0f); assertEquals(Gear.D, b.gear); assertEquals(true, b.lightsOn)
        // Llega sólo voltaje 10 s después: velocidad y marcha ya caducaron.
        val c = mergeCan(b, null, null, null, 13.9f, t0 + 11_000)
        assertNull(c.speedKmh); assertNull(c.gear); assertEquals(13.9f, c.voltageV!!, 0f)
    }

    @Test fun emptyReadingLeavesStateUntouched() {
        val s = mergeCan(CanState(), 10f, null, null, null, 1_000)
        assertSame(s, mergeCan(s, null, null, null, null, 2_000))
    }
}
