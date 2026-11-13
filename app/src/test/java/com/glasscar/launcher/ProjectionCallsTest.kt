package com.glasscar.launcher

import android.app.Notification
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ProjectionCallsTest {
    @After fun cleanup() { ProjectionCalls.clear(); ProjectionCalls.clearVendor() }
    @Test fun observedVendorCallContractDoesNotConfuseSiriOrConnectionWithCalls() {
        ProjectionCalls.vendorStatus(TLINK_PACKAGE, "CONNECTED")
        ProjectionCalls.vendorStatus(TLINK_PACKAGE, "SIRI_ON")
        ProjectionCalls.vendorStatus("other", "PHONE_CALL_ON")
        assertFalse(ProjectionCalls.active.value)
        ProjectionCalls.vendorStatus(TLINK_PACKAGE, "PHONE_CALL_ON")
        assertTrue(ProjectionCalls.active.value)
        ProjectionCalls.clear() // Notification listener reconnect must preserve a vendor call.
        assertTrue(ProjectionCalls.active.value)
        ProjectionCalls.vendorStatus(TLINK_PACKAGE, "PHONE_CALL_OFF")
        assertFalse(ProjectionCalls.active.value)
        ProjectionCalls.vendorStatus(TLINK_PACKAGE, "PHONE_CALL_ON")
        ProjectionCalls.vendorStatus(TLINK_PACKAGE, "DISCONNECT")
        assertFalse(ProjectionCalls.active.value)
    }
    @Test fun ignoresConnectionMusicAndOtherApps() {
        ProjectionCalls.update("a", TLINK_PACKAGE, Notification.CATEGORY_SERVICE)
        ProjectionCalls.update("b", TLINK_PACKAGE, Notification.CATEGORY_TRANSPORT)
        ProjectionCalls.update("c", "other.app", Notification.CATEGORY_CALL)
        assertFalse(ProjectionCalls.active.value)
    }
    @Test fun callRemainsActiveUntilAllCallNotificationsAreRemovedOrReclassified() {
        ProjectionCalls.update("a", TLINK_PACKAGE, Notification.CATEGORY_CALL)
        ProjectionCalls.update("b", TLINK_PACKAGE, Notification.CATEGORY_CALL)
        ProjectionCalls.remove("a")
        assertTrue(ProjectionCalls.active.value)
        ProjectionCalls.update("b", TLINK_PACKAGE, Notification.CATEGORY_SERVICE)
        assertFalse(ProjectionCalls.active.value)
    }
    @Test fun tlinkRoutesInternallyOnlyInEmbeddingVariant() {
        assertTrue(routesToCarPlay(TLINK_PACKAGE, true))
        assertFalse(routesToCarPlay(TLINK_PACKAGE, false))
        assertFalse(routesToCarPlay("other.app", true))
    }
}
