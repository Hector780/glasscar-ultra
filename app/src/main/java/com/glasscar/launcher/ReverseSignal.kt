package com.glasscar.launcher

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 4.7: señal real de reversa del TS10. Solo se escucha; GlassCar nunca escribe en estos canales.
 *
 * Evidencia (inspección estática de com.tw.service 20210731 y com.tw.reverse TS10_eclipse.20230104):
 * - TWService (clase x) recibe `what` 0x9f1c con arg1 = 1 al entrar en reversa y 0 al salir. Ese mensaje es el
 *   que abre com.tw.reverse.ReverseActivity (runnable g, que además escribe Settings.System "revserse_status" = 1
 *   y nunca lo regresa a 0: por eso ese ajuste no sirve como fuente).
 * - En cada cambio TWService difunde com.unisound.intent.action.DO_MUTE / DO_UNMUTE con el extra entero "reverse".
 * - ReverseActivity trata 0x9f1c y 0x20b de la misma forma (arg1 = 1 activa, 0 inactiva).
 */
internal enum class ReverseSource(val label: String, val primary: Boolean) {
    TW_STATE("TW 0x9f1c", true),
    TW_BROADCAST("difusión TWService", true),
    /** Solo decide mientras ninguna fuente principal haya reportado. */
    TW_MCU("TW 0x20b", false)
}

internal data class ReverseReading(val raw: Int, val at: Long, val count: Int)

internal data class ReverseState(
    /** null = todavía sin lectura. Nunca se convierte en «no está en reversa». */
    val active: Boolean? = null,
    /** Flancos de subida (entradas a reversa) desde que arrancó el proceso. */
    val entries: Int = 0,
    /** Flancos de bajada confirmados (de activa a inactiva). */
    val exits: Int = 0,
    val changedAt: Long = 0L,
    val drivenBy: ReverseSource? = null,
    val readings: Map<ReverseSource, ReverseReading> = emptyMap()
)

internal object ReverseProtocol {
    const val STATE_CHANNEL = 0x9f1c
    const val MCU_CHANNEL = 0x20b
    const val BROADCAST_MUTE = "com.unisound.intent.action.DO_MUTE"
    const val BROADCAST_UNMUTE = "com.unisound.intent.action.DO_UNMUTE"
    const val BROADCAST_EXTRA = "reverse"

    /** TWUtil abre los canales como short; se acepta el identificador con o sin extensión de signo. */
    fun source(channel: Int): ReverseSource? = when (channel and 0xffff) {
        STATE_CHANNEL -> ReverseSource.TW_STATE
        MCU_CHANNEL -> ReverseSource.TW_MCU
        else -> null
    }

    fun decode(raw: Int): Boolean? = when (raw) {
        1 -> true
        0 -> false
        else -> null
    }

    /** Pura para poder probarla: el evento vigente más reciente decide; un valor repetido no crea otro flanco. */
    fun reduce(previous: ReverseState, source: ReverseSource, raw: Int, now: Long): ReverseState {
        val seen = previous.readings[source]
        val readings = previous.readings + (source to ReverseReading(raw, now, (seen?.count ?: 0) + 1))
        val value = decode(raw)
        val primarySeen = previous.readings.keys.any { it.primary }
        if (value == null || (!source.primary && primarySeen)) return previous.copy(readings = readings)
        if (value == previous.active) return previous.copy(readings = readings)
        return previous.copy(
            active = value,
            entries = previous.entries + (if (value) 1 else 0),
            exits = previous.exits + (if (!value && previous.active == true) 1 else 0),
            changedAt = now,
            drivenBy = source,
            readings = readings
        )
    }
}

/** Estado único del proceso. Todas las fuentes entregan en el hilo principal. */
internal object ReverseSignal {
    var state by mutableStateOf(ReverseState())
        private set

    fun report(source: ReverseSource, raw: Int, now: Long = SystemClock.elapsedRealtime()) {
        state = ReverseProtocol.reduce(state, source, raw, now)
    }

    fun reset() {
        state = ReverseState()
    }

    fun diagnostics(now: Long = SystemClock.elapsedRealtime()): String {
        val current = state
        val summary = when (current.active) {
            true -> "ACTIVA"
            false -> "inactiva"
            null -> "sin lectura"
        }
        val head = "Reversa: $summary · entradas: ${current.entries} · salidas: ${current.exits}" +
            " · decide: ${current.drivenBy?.label ?: "—"}"
        val lines = ReverseSource.values().map { source ->
            val reading = current.readings[source]
            if (reading == null) "  ${source.label}: sin eventos"
            else "  ${source.label}: crudo=${reading.raw} · hace ${(now - reading.at).coerceAtLeast(0L) / 1000L} s" +
                " · eventos=${reading.count}"
        }
        return (listOf(head) + lines).joinToString("\n")
    }
}

internal enum class CameraSource { FRONT, REAR }

/** Lo que el launcher recuerda entre flancos para cambiar a Cámara una sola vez por transición. */
internal data class ReverseNavigation(
    val seenEntries: Int = 0,
    val seenExits: Int = 0,
    val returnPage: Int? = null,
    val returnSource: CameraSource? = null
)

internal data class ReverseStep(
    val navigation: ReverseNavigation,
    val page: Int? = null,
    val source: CameraSource? = null,
    val entering: Boolean = false
)

internal object ReverseNavigator {
    const val CAMERA_PAGE = 2

    /**
     * Decisión de UX documentada en docs/PENDIENTE-PUSH.md: al entrar en reversa se pasa a Cámara/trasera;
     * al salir se regresa a la página y cámara anteriores solo si el conductor no cambió nada mientras tanto.
     */
    fun advance(navigation: ReverseNavigation, state: ReverseState, currentPage: Int,
        currentSource: CameraSource): ReverseStep {
        val entered = state.entries > navigation.seenEntries
        val exited = state.exits > navigation.seenExits
        val synced = navigation.copy(seenEntries = state.entries, seenExits = state.exits)
        if (entered && state.active == true) {
            return ReverseStep(
                synced.copy(
                    returnPage = navigation.returnPage ?: currentPage,
                    returnSource = navigation.returnSource ?: currentSource
                ),
                page = CAMERA_PAGE, source = CameraSource.REAR, entering = true
            )
        }
        if (entered || exited) {
            // Salida de reversa. También cubre una entrada y salida completas ocurridas con el launcher oculto.
            val untouched = currentPage == CAMERA_PAGE && currentSource == CameraSource.REAR
            return ReverseStep(
                synced.copy(returnPage = null, returnSource = null),
                page = navigation.returnPage?.takeIf { untouched && it != CAMERA_PAGE },
                source = navigation.returnSource?.takeIf { untouched }
            )
        }
        return ReverseStep(synced)
    }
}
