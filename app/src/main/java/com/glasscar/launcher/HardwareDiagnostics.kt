package com.glasscar.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings

/** Read-only evidence for a firmware adapter. No shell, root, or guessed MCU writes. */
object HardwareDiagnostics {
    private val events = ArrayDeque<String>()
    @Synchronized fun recordCan(action: String?, extras: Bundle?) {
        // Keep key/type metadata only, never arbitrary payload strings or identifiers.
        val keys = extras?.keySet()?.sorted()?.take(24)?.joinToString { key ->
            @Suppress("DEPRECATION") val value = extras.get(key)
            val detail = when (value) { is Number, is Boolean -> value.toString(); else -> value?.javaClass?.simpleName ?: "null" }
            "$key=$detail"
        }.orEmpty()
        events.addLast("${SystemClock.elapsedRealtime()} ms · $action · $keys")
        while (events.size > 20) events.removeFirst()
    }
    @Synchronized private fun canEvents(): String = events.joinToString("\n").ifBlank { "Sin broadcasts observados en esta sesión" }

    fun report(context: Context, apps: List<AppEntry>, config: LauncherConfig, can: CanState): String = buildString {
        appendLine("GlassCar ${BuildConfig.VERSION_NAME} · diagnóstico local")
        appendLine("Fabricante: ${Build.MANUFACTURER} · modelo: ${Build.MODEL}")
        appendLine("Producto: ${Build.PRODUCT} · dispositivo: ${Build.DEVICE} · hardware: ${Build.HARDWARE}")
        appendLine("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · compilación ${Build.DISPLAY}")
        val dm = context.resources.displayMetrics
        appendLine("Pantalla: ${dm.widthPixels}×${dm.heightPixels}px · ${dm.densityDpi}dpi · escala ${config.uiScale}")
        appendLine("Audio: ${if (config.nativeAudio) "estéreo (TW si está disponible)" else "STREAM_MUSIC Android"}")
        val audio = context.getSystemService(AudioManager::class.java)
        appendLine("STREAM_MUSIC: ${audio?.getStreamVolume(AudioManager.STREAM_MUSIC)}/${audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} · fijo: ${audio?.isVolumeFixed}")
        appendLine(TopwayAudio.report())
        appendLine("Brillo Android: ${Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)}/255 · permiso escritura: ${Settings.System.canWrite(context)}")
        appendLine("Modo brillo: ${Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)} (0 manual / 1 automático)")
        val now = SystemClock.elapsedRealtime()
        appendLine("CAN velocidad: ${can.speedKmh?.takeIf { can.speedIsFresh(now) } ?: "sin lectura vigente"}")
        appendLine("CAN marcha: ${can.gear?.takeIf { can.gearIsFresh(now) } ?: "sin lectura vigente"}")
        appendLine("CAN luces: ${can.lightsOn ?: "sin dato"}")
        appendLine("Cámaras Android:")
        val manager = context.getSystemService(CameraManager::class.java)
        val cameras = availableCameras(context)
        if (cameras.isEmpty()) appendLine("Ninguna expuesta por Camera2")
        for (id in cameras) appendLine(runCatching {
            val c = manager.getCameraCharacteristics(id)
            "ID $id · facing=${c.get(CameraCharacteristics.LENS_FACING)} · nivel=${c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}"
        }.getOrDefault("ID $id · no se pudieron consultar características"))
        for ((slot, shortcut) in listOf("maps" to DockShortcuts[0], "carplay" to QuickShortcuts[6], "camera" to QuickShortcuts[3])) {
            val app = resolveApp(apps, config, slot, shortcut)
            appendLine("$slot: ${app?.componentKey ?: "no asignada"}")
            if (app != null) {
                val version = runCatching { context.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                appendLine("  versión: $version · widgets: ${matchingWidgets(context, app).size}")
            }
        }
        for (permission in listOf("android.permission.ACTIVITY_EMBEDDING", "android.permission.MANAGE_ACTIVITY_TASKS", "android.permission.INJECT_EVENTS")) {
            appendLine("$permission: ${context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED}")
        }
        appendLine("Broadcasts candidatos recibidos (no confirma protocolo CAN):")
        appendLine(canEvents())
    }
}

fun matchingWidgets(context: Context, app: AppEntry?) = AppWidgetManager.getInstance(context).installedProviders
    .filter { app != null && it.provider.packageName == app.packageName }
