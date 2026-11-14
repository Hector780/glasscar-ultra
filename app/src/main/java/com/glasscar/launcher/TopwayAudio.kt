package com.glasscar.launcher

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.reflect.InvocationTargetException
import kotlin.math.roundToInt

/** Protocol independently identified in the supplied TS10 System UI APK; see docs/TS10-AUDIO.md. */
internal object TopwayVolumeProtocol {
    const val CHANNEL = 0x203
    /** 4.6: MCU night/ILL channel; TWService reads arg1 == 1 as nightMode (TW.apk, class x, what 516). */
    const val LIGHT_CHANNEL = 0x204
    const val QUERY = 0xff
    const val SET_LEVEL = 1
    const val FRESH_MS = 5_000L
    fun decode(arg1: Int, arg2: Int): TopwayVolume? {
        val level = arg1 and Int.MAX_VALUE
        val maximum = arg2 and Int.MAX_VALUE
        // Reject corrupt/unexpected ranges; do not assume a fixed MCU maximum.
        if (maximum !in 1..255 || level !in 0..maximum) return null
        return TopwayVolume(level, maximum, arg1 < 0)
    }
}

internal data class TopwayVolume(val level: Int, val maximum: Int, val muted: Boolean) {
    val fraction: Float get() = if (muted) 0f else level.toFloat() / maximum
}

internal data class TopwayAudioState(
    val status: String = "Sin conexión TS10",
    val volume: TopwayVolume? = null,
    val receivedAt: Long = 0,
    val readings: Int = 0,
    val pendingLevel: Int? = null,
    val writeBlocked: Boolean = false,
    val lastWriteResult: Int? = null,
    val confirmedLevel: Int? = null,
    val nightLights: Boolean? = null,
    /** 4.7: true si esta conexión también escucha los canales de reversa; null sin conexión. */
    val reverseListening: Boolean? = null,
    /** 4.7: la conexión TWUtil del perfil TS10 está abierta (el brillo y el volumen son del MCU). */
    val connected: Boolean = false,
    /** 4.7: brillo físico leído y pedido por esta misma conexión. */
    val brightness: TopwayBrightnessState = TopwayBrightnessState()
) {
    fun usable(now: Long) = volume != null && !writeBlocked &&
        now - receivedAt in 0..TopwayVolumeProtocol.FRESH_MS
}

internal interface TopwayAudioTransport {
    fun open(): Int
    fun addHandler(handler: Handler)
    fun start()
    fun query(): Int
    fun setLevel(level: Int): Int
    fun queryLights(): Int = 0
    /** 4.7: true when open() also subscribed the listen-only reverse channels. */
    fun listensReverse(): Boolean = false
    /** 4.7: write(258, 255), the blmode query of the native brightness panel. */
    fun queryBacklightMode(): Int = 0
    /** 4.7: write(516, night, level, null), the native brightness panel's own write. Negative = rejected. */
    fun setBacklight(night: Int, level: Int): Int = -1
    fun close()
}

/** All calls and callbacks stay on the main looper, just as in the OEM UI. */
internal class TopwayAudioController(
    private val factory: () -> TopwayAudioTransport,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val main: Handler = Handler(Looper.getMainLooper()),
    /** 4.7: reverse readings (source, raw arg1) from the same TW connection. Listen-only. */
    private val onReverse: (ReverseSource, Int) -> Unit = { _, _ -> }
) {
    var state by mutableStateOf(TopwayAudioState())
        private set
    private var transport: TopwayAudioTransport? = null
    private var generation = 0
    private var poll: Runnable? = null
    private var confirmation: Runnable? = null
    private var brightnessConfirmation: Runnable? = null
    private var polls = 0

    fun start(supported: Boolean) {
        stop()
        if (!supported) {
            state = TopwayAudioState(status = "Perfil TS10 no compatible")
            return
        }
        state = TopwayAudioState(status = "Conectando con TW")
        val session = generation
        try {
            val tw = factory()
            transport = tw
            val result = tw.open()
            if (result != 0) {
                fail("TW rechazó la conexión: $result")
                return
            }
            tw.addHandler(Handler(Looper.getMainLooper()) { message ->
                if (session == generation && transport != null && message.what == TopwayVolumeProtocol.CHANNEL) {
                    receive(message.arg1, message.arg2)
                } else if (session == generation && transport != null && message.what == TopwayVolumeProtocol.LIGHT_CHANNEL) {
                    state = state.copy(nightLights = TopwayMapping.flag(message.arg1))
                    receiveBacklight(message.arg1, message.arg2)
                } else if (session == generation && transport != null && message.what == TopwayBrightnessProtocol.MODE_CHANNEL) {
                    state = state.copy(brightness = state.brightness.copy(mode = TopwayBrightnessProtocol.mode(message.arg2)))
                } else if (session == generation && transport != null) {
                    val reverseSource = ReverseProtocol.source(message.what)
                    if (reverseSource != null) onReverse(reverseSource, message.arg1)
                }
                true
            })
            tw.start()
            state = state.copy(status = "Esperando volumen del estéreo", reverseListening = tw.listensReverse(),
                connected = true)
            polls = 0
            poll = object : Runnable {
                override fun run() {
                    if (generation != session || transport == null) return
                    try {
                        val code = tw.query()
                        runCatching { tw.queryLights() }
                        // blmode rarely changes: ask at start and every fifth poll (10 s); pushes arrive at once.
                        if (polls % 5 == 0) runCatching { tw.queryBacklightMode() }
                        polls++
                        if (code < 0) { fail("TW rechazó la consulta: $code"); return }
                        if (state.volume != null && !state.usable(clock()) && !state.writeBlocked) {
                            state = state.copy(volume = null, status = "Sin lectura reciente del estéreo")
                        }
                        main.postDelayed(this, 2_000)
                    } catch (e: Exception) { fail(errorName(e)) }
                    catch (e: LinkageError) { fail(e.javaClass.simpleName) }
                }
            }.also { main.post(it) }
        } catch (e: Exception) { fail(errorName(e)) }
        catch (e: LinkageError) { fail(e.javaClass.simpleName) }
    }

    private fun receive(arg1: Int, arg2: Int) {
        val reading = TopwayVolumeProtocol.decode(arg1, arg2)
        if (reading == null) {
            state = state.copy(volume = null, status = "Lectura TW fuera de rango")
            return
        }
        val confirmed = state.pendingLevel == reading.level
        if (confirmed) { confirmation?.let(main::removeCallbacks); confirmation = null }
        state = state.copy(
            volume = reading, receivedAt = clock(), readings = state.readings + 1,
            status = when {
                state.writeBlocked -> state.status
                confirmed -> "Volumen confirmado por el estéreo"
                state.pendingLevel != null -> "Esperando confirmación del estéreo"
                else -> "Volumen nativo recibido"
            },
            pendingLevel = if (confirmed) null else state.pendingLevel,
            confirmedLevel = if (confirmed) reading.level else state.confirmedLevel
        )
    }

    /** Called only on a user gesture. A successful call is not treated as a confirmed volume change. */
    fun setFraction(fraction: Float): Boolean {
        val tw = transport ?: return false
        val reading = state.volume ?: return false
        if (!fraction.isFinite() || !state.usable(clock()) || state.pendingLevel != null) return false
        val target = (fraction.coerceIn(0f, 1f) * reading.maximum).roundToInt()
        if (target == reading.level && !reading.muted) return true
        val session = generation
        state = state.copy(pendingLevel = target, confirmedLevel = null, status = "Esperando confirmación del estéreo")
        try {
            val code = tw.setLevel(target)
            state = state.copy(lastWriteResult = code)
            if (code < 0) {
                state = state.copy(pendingLevel = null, writeBlocked = true, status = "TW rechazó el cambio: $code")
                return false
            }
            if (state.pendingLevel != null) {
                confirmation = Runnable {
                    if (generation == session && state.pendingLevel != null) {
                        state = state.copy(pendingLevel = null, writeBlocked = true,
                            status = "Cambio sin confirmar; usa el panel del estéreo")
                    }
                }.also { main.postDelayed(it, 3_000) }
            }
            return true
        } catch (e: Exception) { fail(errorName(e)); return false }
        catch (e: LinkageError) { fail(e.javaClass.simpleName); return false }
    }

    private fun receiveBacklight(arg1: Int, arg2: Int) {
        val before = state.brightness
        val reading = TopwayBrightnessProtocol.decode(arg1, arg2)
        if (reading == null) {
            state = state.copy(brightness = before.copy(reading = null, status = "Lectura de brillo fuera de rango"))
            return
        }
        val pending = before.pending
        val written = if (before.requestedNight == true) reading.nightLevel else reading.day
        val confirmed = pending != null && written == pending
        if (confirmed) { brightnessConfirmation?.let(main::removeCallbacks); brightnessConfirmation = null }
        state = state.copy(brightness = before.copy(
            reading = reading, receivedAt = clock(), readings = before.readings + 1,
            pending = if (confirmed) null else pending,
            confirmed = if (confirmed) written else before.confirmed,
            status = when {
                confirmed -> "Brillo confirmado por el estéreo"
                pending != null -> "Esperando confirmación del estéreo"
                else -> "Brillo nativo recibido"
            }
        ))
    }

    /**
     * Called only on a user gesture, like the native panel: one write per level, never 0, never without a fresh
     * reading and a known blmode. The stereo's next 516 reading confirms it; nothing is assumed.
     */
    fun setBrightnessFraction(fraction: Float): Boolean {
        val tw = transport ?: return false
        val before = state.brightness
        val night = before.activeNight ?: return false
        if (!fraction.isFinite() || !before.usable(clock())) return false
        val target = TopwayBrightnessProtocol.target(fraction)
        if (target == before.pending) return true
        if (before.pending == null && target == before.level) return true
        val session = generation
        brightnessConfirmation?.let(main::removeCallbacks); brightnessConfirmation = null
        state = state.copy(brightness = before.copy(requested = target, requestedNight = night, pending = target,
            confirmed = null, status = "Esperando confirmación del estéreo"))
        try {
            val code = tw.setBacklight(if (night) 1 else 0, target)
            state = state.copy(brightness = state.brightness.copy(lastWriteResult = code))
            if (code < 0) {
                state = state.copy(brightness = state.brightness.copy(pending = null, status = "TW rechazó el brillo: $code"))
                return false
            }
            if (state.brightness.pending == target) {
                brightnessConfirmation = Runnable {
                    if (generation == session && state.brightness.pending == target) {
                        // Controls fall back to the stereo's own reading: an unconfirmed level is never shown as real.
                        state = state.copy(brightness = state.brightness.copy(pending = null,
                            status = "Brillo sin confirmar por el estéreo"))
                    }
                }.also { main.postDelayed(it, 3_000) }
            }
            return true
        } catch (e: Exception) { fail(errorName(e)); return false }
        catch (e: LinkageError) { fail(e.javaClass.simpleName); return false }
    }

    fun stop() {
        generation++
        poll?.let(main::removeCallbacks); poll = null
        confirmation?.let(main::removeCallbacks); confirmation = null
        brightnessConfirmation?.let(main::removeCallbacks); brightnessConfirmation = null
        val previous = transport
        transport = null
        runCatching { previous?.close() }
        state = TopwayAudioState(status = "Conexión TW pausada")
    }
    private fun fail(reason: String) {
        stop()
        state = TopwayAudioState(status = "TW no disponible: $reason")
    }
    private fun errorName(e: Exception): String =
        ((e as? InvocationTargetException)?.targetException ?: e).javaClass.simpleName
}

/** Uses only public methods of the vendor framework; no hidden-API bypass, copied SDK or native library. */
internal class ReflectiveTopwayAudioTransport : TopwayAudioTransport {
    private val type = Class.forName("android.tw.john.TWUtil")
    private val intType = Int::class.javaPrimitiveType!!
    private val openMethod = type.getMethod("open", ShortArray::class.java)
    private val startMethod = type.getMethod("start")
    private val addMethod = type.getMethod("addHandler", String::class.java, Handler::class.java)
    private val queryMethod = type.getMethod("write", intType, intType)
    private val levelMethod = type.getMethod("write", intType, intType, intType, Any::class.java)
    private val removeMethod = type.getMethod("removeHandler", String::class.java)
    private val stopMethod = type.getMethod("stop")
    private val closeMethod = type.getMethod("close")
    private var instance: Any = type.getConstructor().newInstance()
    private val tag = "GlassCarAudio"
    private var reverse = false
    private var backlightMode = false
    private var opened = false
    private var listening = false
    private var started = false
    override fun open(): Int {
        val base = shortArrayOf(TopwayVolumeProtocol.CHANNEL.toShort(), TopwayVolumeProtocol.LIGHT_CHANNEL.toShort())
        // 4.7: one connection for everything. 0x102 carries blmode for the brightness contract; 0x9f1c/0x20b are
        // the reverse channels (listen only). SystemUI's own TWUtil opens 0x204, 0x102, 0x203 and 0x9f1c together.
        val withMode = shortArrayOf(TopwayVolumeProtocol.CHANNEL.toShort(), TopwayVolumeProtocol.LIGHT_CHANNEL.toShort(),
            TopwayBrightnessProtocol.MODE_CHANNEL.toShort())
        val full = shortArrayOf(TopwayVolumeProtocol.CHANNEL.toShort(), TopwayVolumeProtocol.LIGHT_CHANNEL.toShort(),
            TopwayBrightnessProtocol.MODE_CHANNEL.toShort(),
            ReverseProtocol.STATE_CHANNEL.toShort(), ReverseProtocol.MCU_CHANNEL.toShort())
        reverse = false; backlightMode = false
        // Never lose the confirmed volume/lights connection because of the extra channels: fall back step by step
        // to the 4.6 list, each time on a fresh TWUtil instance.
        for (attempt in 0..1) {
            val channels = if (attempt == 0) full else withMode
            val accepted = runCatching { (openMethod.invoke(instance, channels) as Number).toInt() }.getOrDefault(-1) == 0
            if (accepted) {
                backlightMode = true; reverse = attempt == 0; opened = true
                return 0
            }
            runCatching { closeMethod.invoke(instance) }
            instance = type.getConstructor().newInstance()
        }
        val result = (openMethod.invoke(instance, base) as Number).toInt()
        opened = result == 0
        return result
    }
    override fun listensReverse() = reverse
    override fun queryBacklightMode(): Int {
        if (!backlightMode) return -1
        return (queryMethod.invoke(instance, TopwayBrightnessProtocol.MODE_CHANNEL, TopwayBrightnessProtocol.QUERY) as Number).toInt()
    }
    // Same call as SystemUI's TWSystemUI.write(516, mode, value): TWUtil.write(int, int, int, Object) with null.
    override fun setBacklight(night: Int, level: Int) = (levelMethod.invoke(instance, TopwayBrightnessProtocol.CHANNEL,
        night, level, null) as Number).toInt()
    override fun addHandler(handler: Handler) { addMethod.invoke(instance, tag, handler); listening = true }
    override fun start() { startMethod.invoke(instance); started = true }
    override fun query() = (queryMethod.invoke(instance, TopwayVolumeProtocol.CHANNEL, TopwayVolumeProtocol.QUERY) as Number).toInt()
    override fun queryLights() = (queryMethod.invoke(instance, TopwayVolumeProtocol.LIGHT_CHANNEL, TopwayVolumeProtocol.QUERY) as Number).toInt()
    override fun setLevel(level: Int) = (levelMethod.invoke(instance, TopwayVolumeProtocol.CHANNEL,
        TopwayVolumeProtocol.SET_LEVEL, level, null) as Number).toInt()
    override fun close() {
        if (listening) runCatching { removeMethod.invoke(instance, tag) }
        if (started) runCatching { stopMethod.invoke(instance) }
        if (opened) runCatching { closeMethod.invoke(instance) }
        listening = false; started = false; opened = false
    }
}

internal object TopwayAudio {
    val controller by lazy {
        TopwayAudioController(::ReflectiveTopwayAudioTransport,
            onReverse = { source, raw -> ReverseSignal.report(source, raw) })
    }
    fun start(context: Context) {
        // Intentionally restrict this first adapter to the inspected UIS7862/API29 firmware family.
        val matched = Build.VERSION.SDK_INT == 29 && Build.HARDWARE == "ums512_1h10" && runCatching {
            context.packageManager.getPackageInfo("com.tw.service", 0).versionName == "20210731" &&
                context.packageManager.getPackageInfo("com.dofun.carsetting", 0).longVersionCode == 551L
        }.getOrDefault(false)
        controller.start(matched)
    }
    fun stop() = controller.stop()
    fun report(): String {
        val s = controller.state
        val v = s.volume
        return "TW audio: ${s.status}\n" +
            "TW nivel: ${v?.level ?: "sin lectura"}/${v?.maximum ?: "?"} · silencio: ${v?.muted ?: "?"}\n" +
            "TW lecturas: ${s.readings} · vigente: ${s.usable(SystemClock.elapsedRealtime())}\n" +
            "TW luces (MCU 0x204): ${s.nightLights?.let { if (it) "encendidas/noche" else "apagadas/día" } ?: "sin lectura"}\n" +
            "TW reversa (escucha 0x9f1c/0x20b): ${when (s.reverseListening) { true -> "canales aceptados"; false -> "canales rechazados; queda la difusión de TWService"; null -> "sin conexión" }}\n" +
            "TW solicitado: ${s.pendingLevel ?: "—"} · confirmado: ${s.confirmedLevel ?: "—"} · retorno: ${s.lastWriteResult ?: "—"}\n" +
            brightnessReport(s.brightness, SystemClock.elapsedRealtime())
    }
    internal fun brightnessReport(b: TopwayBrightnessState, now: Long): String {
        val reading = b.reading
        val mode = when (b.mode) {
            TopwayBrightnessProtocol.MODE_AUTO -> "0 automático"
            TopwayBrightnessProtocol.MODE_DAY -> "1 día"
            TopwayBrightnessProtocol.MODE_NIGHT -> "2 noche"
            else -> "sin lectura"
        }
        val active = when (b.activeNight) { true -> "noche"; false -> "día"; null -> "desconocido" }
        val slot = when (b.requestedNight) { true -> " (noche)"; false -> " (día)"; null -> "" }
        return "TW brillo (MCU 516, máx ${TopwayBrightnessProtocol.MAXIMUM}): ${b.status}\n" +
            "TW brillo día: ${reading?.day ?: "sin lectura"} · noche: ${reading?.nightLevel ?: "sin lectura"}" +
            " · gobierna: $active · blmode (258): $mode\n" +
            "TW brillo lecturas: ${b.readings} · vigente: ${b.usable(now)}\n" +
            "TW brillo solicitado: ${b.requested ?: "—"}$slot · pendiente: ${b.pending ?: "—"}" +
            " · confirmado: ${b.confirmed ?: "—"} · retorno: ${b.lastWriteResult ?: "—"}"
    }
}
