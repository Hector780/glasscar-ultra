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
    val nightLights: Boolean? = null
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
    fun close()
}

/** All calls and callbacks stay on the main looper, just as in the OEM UI. */
internal class TopwayAudioController(
    private val factory: () -> TopwayAudioTransport,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val main: Handler = Handler(Looper.getMainLooper())
) {
    var state by mutableStateOf(TopwayAudioState())
        private set
    private var transport: TopwayAudioTransport? = null
    private var generation = 0
    private var poll: Runnable? = null
    private var confirmation: Runnable? = null

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
                }
                true
            })
            tw.start()
            state = state.copy(status = "Esperando volumen del estéreo")
            poll = object : Runnable {
                override fun run() {
                    if (generation != session || transport == null) return
                    try {
                        val code = tw.query()
                        runCatching { tw.queryLights() }
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

    fun stop() {
        generation++
        poll?.let(main::removeCallbacks); poll = null
        confirmation?.let(main::removeCallbacks); confirmation = null
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
    private val instance = type.getConstructor().newInstance()
    private val tag = "GlassCarAudio"
    private var opened = false
    private var listening = false
    private var started = false
    override fun open(): Int {
        val result = (openMethod.invoke(instance, shortArrayOf(TopwayVolumeProtocol.CHANNEL.toShort(),
            TopwayVolumeProtocol.LIGHT_CHANNEL.toShort())) as Number).toInt()
        opened = result == 0
        return result
    }
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
    val controller by lazy { TopwayAudioController(::ReflectiveTopwayAudioTransport) }
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
            "TW solicitado: ${s.pendingLevel ?: "—"} · confirmado: ${s.confirmedLevel ?: "—"} · retorno: ${s.lastWriteResult ?: "—"}"
    }
}
