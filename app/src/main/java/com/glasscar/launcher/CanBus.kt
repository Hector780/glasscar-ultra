package com.glasscar.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

// =====================================================================
//  CANbus v3 (Optimizado para TS10 / UIS7862 + SimpleSoft RP5-FD-002 Ford Escape 2010)
// =====================================================================

object CanConfig {
    const val GLASSCAR_ACTION = "com.glasscar.launcher.CAN_UPDATE"

    /**
     * Intents nativos reales de unidades TS10 (Topway), FYT, Microntek y XYAuto
     * al comunicarse con decodificadores SimpleSoft (XP) RP5-FD-002.
     */
    val EXTRA_ACTIONS: List<String> = listOf(
        "com.tw.canbus.ACTION_CANBUS_DATA",
        "com.topway.canbus.ACTION_CANBUS_UPDATE",
        "com.microntek.canbusdisplay",
        "com.fyt.canbus.action",
        "android.intent.action.CAMERA_STATUS",
        "action.reverse.status",
        "com.ankai.canbus.action",
        "com.xyauto.broadcast.CANBUS_RECEIVE",
        "com.ts.can.CanCarInfoBroadcast"
    )

    /** Nombres de extras que se interpretan como velocidad (km/h). */
    val SPEED_KEYS = listOf("speed", "vehicle_speed", "car_speed", "spd", "velocity", "cur_speed")

    /** Nombres de extras que se interpretan como marcha. */
    val GEAR_KEYS = listOf("gear", "gear_position", "shift", "transmission", "gearbox")

    /** Extras booleanos o enteros que indican reversa en TS10 / SimpleSoft. */
    val REVERSE_KEYS = listOf("reverse", "backcar", "back_car", "is_reverse", "camera_status", "reversing")

    /** Extras que indican luces encendidas (ILL / Faros de la Escape). */
    val ILL_KEYS = listOf("ill", "illumination", "headlight", "lights", "lamp", "small_light")

    /** Tiempo durante el cual un dato CAN se considera vigente. */
    const val FRESH_MS = 5_000L

    const val LOG_TAG = "GlassCarCAN"
}

enum class Gear { P, R, N, D, S }

data class CanState(
    val speedKmh: Float? = null,
    val gear: Gear? = null,
    val lightsOn: Boolean? = null,
    val updatedAt: Long = 0L,
    val voltageV: Float? = null,
    val voltageAt: Long = 0L,
    val speedAt: Long = updatedAt,
    val gearAt: Long = updatedAt
) {
    fun speedIsFresh(now: Long) = speedAt > 0 && now - speedAt < CanConfig.FRESH_MS
    fun gearIsFresh(now: Long) = gearAt > 0 && now - gearAt < CanConfig.FRESH_MS
    fun isFresh(now: Long = SystemClock.elapsedRealtime()): Boolean =
        updatedAt > 0L && now - updatedAt < CanConfig.FRESH_MS
}

class CanBusSource(private val context: Context) {
    private val _state = MutableStateFlow(CanState())
    val state: StateFlow<CanState> = _state
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val extras = intent.extras
            Log.d(CanConfig.LOG_TAG, "action=${intent.action} extras=${describe(extras)}")
            if (extras == null) return
            val speed = parseSpeed(extras)
            val gear = parseGear(extras)
            val lights = parseBoolean(extras, CanConfig.ILL_KEYS)
            val voltage = parseVoltage(extras)
            if (speed == null && gear == null && lights == null && voltage == null) return
            val previous = _state.value
            _state.value = CanState(
                speedKmh = speed ?: previous.speedKmh.takeIf { previous.isFresh() },
                gear = gear ?: previous.gear.takeIf { previous.isFresh() },
                lightsOn = lights ?: previous.lightsOn,
                updatedAt = SystemClock.elapsedRealtime(),
                voltageV = voltage ?: previous.voltageV,
                voltageAt = if (voltage != null) SystemClock.elapsedRealtime() else previous.voltageAt,
                speedAt = if (speed != null) SystemClock.elapsedRealtime() else previous.speedAt,
                gearAt = if (gear != null) SystemClock.elapsedRealtime() else previous.gearAt
            )
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter(CanConfig.GLASSCAR_ACTION)
        CanConfig.EXTRA_ACTIONS.forEach { filter.addAction(it) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    @Suppress("DEPRECATION")
    private fun valueOf(extras: Bundle, keys: List<String>): Any? {
        for (key in extras.keySet()) {
            val k = key.lowercase(Locale.ROOT)
            if (keys.any { k == it || k.endsWith("_$it") || k.endsWith(".$it") }) return extras.get(key)
        }
        return null
    }

    private fun parseVoltage(extras: Bundle): Float? {
        val volts = valueOf(extras, listOf("battery_voltage_v", "battery_voltage", "voltage_v"))
            ?.toString()?.toFloatOrNull()?.takeIf { it in 6f..30f }
        val mv = valueOf(extras, listOf("battery_voltage_mv"))?.toString()?.toFloatOrNull()
            ?.div(1000f)?.takeIf { it in 6f..30f }
        return volts ?: mv
    }

    private fun parseSpeed(extras: Bundle): Float? = when (val v = valueOf(extras, CanConfig.SPEED_KEYS)) {
        is Int -> v.toFloat()
        is Long -> v.toFloat()
        is Float -> v
        is Double -> v.toFloat()
        is String -> v.trim().toFloatOrNull()
        else -> null
    }?.takeIf { it in 0f..400f }

    private fun parseBoolean(extras: Bundle, keys: List<String>): Boolean? = when (val v = valueOf(extras, keys)) {
        is Boolean -> v
        is Int -> v == 1
        is String -> v == "1" || v.equals("true", ignoreCase = true) || v.equals("on", ignoreCase = true)
        else -> null
    }

    private fun parseGear(extras: Bundle): Gear? {
        val reverse = parseBoolean(extras, CanConfig.REVERSE_KEYS)
        if (reverse == true) return Gear.R
        if (reverse == false && _state.value.gear == Gear.R) return Gear.P
        val raw = valueOf(extras, CanConfig.GEAR_KEYS)?.toString() ?: return null
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "p", "park", "parking", "0" -> Gear.P
            "r", "reverse", "1" -> Gear.R
            "n", "neutral", "2" -> Gear.N
            "d", "drive", "3" -> Gear.D
            "s", "sport", "4" -> Gear.S
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun describe(extras: Bundle?): String =
        extras?.keySet()?.joinToString(prefix = "{", postfix = "}") { "$it=${extras.get(it)}" } ?: "{}"
}

// =====================================================================
//  Lógica híbrida (GPS + CANbus Ford Escape 2010)
// =====================================================================

enum class SpeedSource { GPS, CAN, NONE }

data class ResolvedSpeed(val kmh: Float, val source: SpeedSource)

/** Velocidad principal por GPS; si no hay señal GPS, usa la del CAN si está vigente. */
fun resolveSpeed(drive: DriveState, can: CanState, now: Long): ResolvedSpeed {
    val canSpeed = can.speedKmh
    return when {
        drive.isFresh(now) -> ResolvedSpeed(drive.speedKmh, SpeedSource.GPS)
        canSpeed != null && can.speedIsFresh(now) -> ResolvedSpeed(canSpeed, SpeedSource.CAN)
        else -> ResolvedSpeed(0f, SpeedSource.NONE)
    }
}

/**
 * Prioridad de la marcha mostrada:
 * 1. Reversa reportada por CAN (seguridad: siempre gana).
 * 2. Selección manual tocando la píldora.
 * 3. Marcha reportada por CAN.
 * 4. Automática: P detenido (< 1.5 km/h), D en movimiento (>= 1.5 km/h).
 */
fun resolveGear(manual: Gear?, can: CanState, speedKmh: Float, now: Long): Gear {
    val canGear = can.gear.takeIf { can.gearIsFresh(now) }
    if (canGear == Gear.R) return Gear.R
    return manual ?: canGear ?: if (speedKmh >= 1.5f) Gear.D else Gear.P
}

