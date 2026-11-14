package com.glasscar.launcher

import kotlin.math.roundToInt

/**
 * 4.7: brillo físico del TS10. La retroiluminación es del MCU; Settings.System no la mueve.
 *
 * Contrato observado (inspección estática, sin ejecutar nada):
 * - SystemUI `QSPanelBrightness$1.onProgressChanged`: `TWUtil.write(516, modo, valor, null)`.
 *   modo = nightMode si blmode == 0 (automático); 0 si blmode == 1 (día); 1 si blmode == 2 (noche).
 * - `QSPanelBrightness.initBrightValue`: `SeekBar.setMax(10)`. `TWService.Qa` (tecla de brillo) recorre 10 → 5 → 1.
 * - Lectura `what` 516: arg1 == 1 es noche; arg2 and 255 es el brillo de día; (arg2 shr 8) and 255 el de noche.
 *   Consulta: `write(516, 255)`.
 * - Lectura `what` 258 (0x102): arg2 es blmode. Consulta: `write(258, 255)` (QSPanelBrightness.initView).
 * - TWService (clase x, casos 0x204 y 0x102) aplica la misma tabla: blmode 1 usa el brillo de día y 2 el de noche.
 */
internal object TopwayBrightnessProtocol {
    /** 516: el mismo canal que ya leía las luces en 4.6. */
    const val CHANNEL = 0x204
    /** 258: modo de retroiluminación (blmode). */
    const val MODE_CHANNEL = 0x102
    const val QUERY = 0xff
    /** Máximo real del deslizador nativo. No es un porcentaje ni 255. */
    const val MAXIMUM = 10
    /** La tecla de brillo del propio estéreo nunca baja de 1; GlassCar tampoco escribe 0. */
    const val MINIMUM = 1
    const val MODE_AUTO = 0
    const val MODE_DAY = 1
    const val MODE_NIGHT = 2
    const val FRESH_MS = 5_000L

    fun decode(arg1: Int, arg2: Int): TopwayBacklight? {
        val night = TopwayMapping.flag(arg1) ?: return null
        val day = arg2 and 0xff
        val nightLevel = (arg2 shr 8) and 0xff
        // Un nivel fuera de 0..10 no corresponde al contrato inspeccionado: no se interpreta ni se escribe.
        if (day > MAXIMUM || nightLevel > MAXIMUM) return null
        return TopwayBacklight(night, day, nightLevel)
    }

    fun mode(arg2: Int): Int? = arg2.takeIf { it == MODE_AUTO || it == MODE_DAY || it == MODE_NIGHT }

    /** Qué brillo gobierna el panel ahora mismo. null si falta blmode: no se adivina. */
    fun activeNight(mode: Int?, night: Boolean): Boolean? = when (mode) {
        MODE_AUTO -> night
        MODE_DAY -> false
        MODE_NIGHT -> true
        else -> null
    }

    fun target(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * MAXIMUM).roundToInt().coerceIn(MINIMUM, MAXIMUM)
}

internal data class TopwayBacklight(val night: Boolean, val day: Int, val nightLevel: Int)

internal data class TopwayBrightnessState(
    val reading: TopwayBacklight? = null,
    val receivedAt: Long = 0L,
    val readings: Int = 0,
    /** blmode crudo validado (0 automático, 1 día, 2 noche); null sin lectura. */
    val mode: Int? = null,
    val requested: Int? = null,
    val requestedNight: Boolean? = null,
    val pending: Int? = null,
    val confirmed: Int? = null,
    val lastWriteResult: Int? = null,
    val status: String = "Sin lectura de brillo"
) {
    val activeNight: Boolean? get() = reading?.let { TopwayBrightnessProtocol.activeNight(mode, it.night) }
    /** Nivel leído del estéreo para el modo que gobierna el panel. */
    val level: Int? get() {
        val current = reading ?: return null
        val night = activeNight ?: return null
        return if (night) current.nightLevel else current.day
    }
    /** Lo que deben mostrar los controles: el cambio en curso o, si no hay, la lectura del estéreo. */
    val shownLevel: Int? get() = pending ?: level
    fun usable(now: Long) = level != null && now - receivedAt in 0..TopwayBrightnessProtocol.FRESH_MS
}

/**
 * Único modelo de brillo para la barra inferior y el futuro centro de control.
 * `native` = el estéreo TS10 está conectado y su brillo es del MCU; `ready` = hay lectura vigente para escribir.
 */
internal data class BrightnessModel(val native: Boolean, val ready: Boolean, val fraction: Float, val level: Int?) {
    val maximum: Int get() = TopwayBrightnessProtocol.MAXIMUM
}

internal fun brightnessModel(tw: TopwayAudioState, androidFraction: Float, now: Long): BrightnessModel {
    if (!tw.connected) return BrightnessModel(native = false, ready = false, fraction = androidFraction, level = null)
    val shown = tw.brightness.shownLevel
    return BrightnessModel(
        native = true,
        ready = tw.brightness.usable(now),
        fraction = if (shown == null) 0f else shown.toFloat() / TopwayBrightnessProtocol.MAXIMUM,
        level = shown
    )
}
