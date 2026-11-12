package com.glasscar.launcher

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class CameraHardwareDiagnosticsTest {
    private fun describe(vendor: Int, product: Int, vararg interfaces: CameraHardwareDiagnostics.InterfaceInfo) =
        CameraHardwareDiagnostics.describe(vendor, product, interfaces.toList())

    @Test fun knownDvrStorageIsNotMisreportedAsUvcOrConfirmedVideo() {
        val report = describe(0x1b3f, 0x8301, CameraHardwareDiagnostics.InterfaceInfo(8, 6, 80))
        assertTrue(report.contains("ID reconocido por el APK HD Car DVR"))
        assertTrue(report.contains("imagen no comprobada"))
        assertTrue(report.contains("almacenamiento USB"))
        assertFalse(report.contains("interfaz USB de video"))
    }
    @Test fun unrelatedStorageAndPartialIdMatchesDoNotIdentifyDvr() {
        for ((vid, pid) in listOf(0x1234 to 0x8301, 0x1b3f to 0x1234, 0x1234 to 0x5678)) {
            assertFalse(describe(vid, pid, CameraHardwareDiagnostics.InterfaceInfo(8, 6, 80)).contains("ID reconocido"))
        }
    }
    @Test fun compositeDeviceRetainsVideoAndStorageEvidence() {
        val report = describe(1, 2, CameraHardwareDiagnostics.InterfaceInfo(8, 6, 80), CameraHardwareDiagnostics.InterfaceInfo(14, 2, 0))
        assertTrue(report.contains("interfaz USB de video"))
        assertTrue(report.contains("almacenamiento USB"))
        assertTrue(report.contains("compatibilidad de vista previa no comprobada"))
    }
    @Test fun unknownAndOtherStorageProtocolsDoNotInventCameraRoute() {
        assertEquals("sin ruta de cámara identificada", describe(1, 2))
        assertEquals("sin ruta de cámara identificada", describe(1, 2, CameraHardwareDiagnostics.InterfaceInfo(8, 6, 98)))
    }
    @Test fun missingUsbServiceAndEmptyInventoryHaveDifferentResults() {
        assertTrue(CameraHardwareDiagnostics.usbInventory(null).contains("Servicio USB no disponible"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = CameraHardwareDiagnostics.usbInventory(context.getSystemService(UsbManager::class.java))
        assertTrue(report.contains("Ningún dispositivo USB visible"))
        assertFalse(report.contains("Servicio USB no disponible"))
    }
    @Test fun reportRunsWithoutCameraOrUsbPermissionAndNamesMissingOemApps() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = CameraHardwareDiagnostics.report(context)
        assertTrue(report.contains("Diagnóstico de cámara/USB v1"))
        assertTrue(report.contains("com.tw.reverse · no instalada o no visible"))
        assertTrue(report.contains("com.williexing.android.apps.xcdvr1 · no instalada o no visible"))
    }
}
