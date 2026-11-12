package com.glasscar.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import java.util.Locale

/** Descriptor-only inventory: never opens, claims, resets or sends data to USB devices. */
object CameraHardwareDiagnostics {
    internal data class InterfaceInfo(val klass: Int, val subclass: Int, val protocol: Int)

    internal fun describe(vendor: Int, product: Int, interfaces: List<InterfaceInfo>): String {
        val routes = mutableListOf<String>()
        if (vendor == 0x1b3f && product == 0x8301) {
            routes += "ID reconocido por el APK HD Car DVR; imagen no comprobada"
        }
        if (interfaces.any { it.klass == UsbConstants.USB_CLASS_VIDEO }) {
            routes += "interfaz USB de video; compatibilidad de vista previa no comprobada"
        }
        if (interfaces.any { it.klass == UsbConstants.USB_CLASS_MASS_STORAGE && it.subclass == 6 && it.protocol == 80 }) {
            routes += "almacenamiento USB (SCSI/Bulk); no confirma una cámara UVC"
        }
        return routes.joinToString(" · ").ifBlank { "sin ruta de cámara identificada" }
    }

    private fun hex(value: Int) = String.format(Locale.ROOT, "%04x", value)

    internal fun usbInventory(manager: UsbManager?): String = buildString {
        appendLine("USB conectado (solo descriptores, sin abrir dispositivos):")
        if (manager == null) {
            appendLine("Servicio USB no disponible")
            return@buildString
        }
        val devices = try { manager.deviceList.values.toList().sortedWith(compareBy<UsbDevice> { it.vendorId }.thenBy { it.productId }) }
        catch (e: Exception) {
            appendLine("No se pudo enumerar USB: ${e.javaClass.simpleName}")
            return@buildString
        }
        if (devices.isEmpty()) appendLine("Ningún dispositivo USB visible; no determina si existe una cámara analógica")
        devices.take(32).forEachIndexed { index, device ->
            try {
                val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
                val info = interfaces.map { InterfaceInfo(it.interfaceClass, it.interfaceSubclass, it.interfaceProtocol) }
                val permission = runCatching { manager.hasPermission(device).toString() }.getOrDefault("desconocido")
                appendLine("USB ${index + 1}: VID:PID=${hex(device.vendorId)}:${hex(device.productId)} · clase=${device.deviceClass} · permiso GlassCar=$permission")
                appendLine("  ${describe(device.vendorId, device.productId, info)}")
                interfaces.take(32).forEach { intf ->
                    appendLine("  interfaz=${intf.id} alt=${intf.alternateSetting} clase=${intf.interfaceClass}/${intf.interfaceSubclass}/${intf.interfaceProtocol}")
                    (0 until intf.endpointCount.coerceAtMost(32)).forEach { i ->
                        val ep = intf.getEndpoint(i)
                        appendLine("    endpoint=${ep.address} dirección=${if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"} tipo=${ep.type} paquete=${ep.maxPacketSize}")
                    }
                }
            } catch (e: Exception) { appendLine("USB ${index + 1}: consulta incompleta (${e.javaClass.simpleName})") }
        }
        if (devices.size > 32) appendLine("Inventario limitado a 32 dispositivos")
    }

    fun report(context: Context): String = buildString {
        appendLine("Diagnóstico de cámara/USB v1")
        appendLine("Permiso CAMERA: ${context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}")
        appendLine("USB host: ${context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)} · ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        val manager = runCatching { context.getSystemService(UsbManager::class.java) }.getOrNull()
        append(usbInventory(manager))
        appendLine("Aplicaciones de cámara (metadatos; no se inician servicios):")
        for (pkg in listOf("com.williexing.android.apps.xcdvr1", "com.tw.reverse", "com.tw.rightview", "com.sprd.cameraipcontrol")) {
            try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SERVICES)
                appendLine("$pkg · versión=${info.versionName} (${info.longVersionCode})")
                if (pkg == "com.williexing.android.apps.xcdvr1") {
                    val service = info.services?.firstOrNull { it.name == "com.williexing.android.xiot.devices.XUFSCameraService" }
                    appendLine("  XUFSCameraService: ${service?.let { "exportado=${it.exported} · habilitado=${it.enabled} · permiso=${it.permission ?: "ninguno declarado"}" } ?: "no declarado"}")
                }
            } catch (_: PackageManager.NameNotFoundException) {
                appendLine("$pkg · no instalada o no visible")
            } catch (e: Exception) { appendLine("$pkg · consulta fallida (${e.javaClass.simpleName})") }
        }
    }
}
