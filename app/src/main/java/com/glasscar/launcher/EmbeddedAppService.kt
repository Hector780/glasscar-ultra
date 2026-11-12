package com.glasscar.launcher

import android.content.ComponentName
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

/** Runs as shell/root ONLY after the user authorizes Shizuku. Never an exported Android Service. */
@Keep
class EmbeddedAppService : IEmbeddedAppService.Stub() {
    private data class Session(val display: Int, val uid: Int, val target: String,
        val lifetime: IBinder, val death: IBinder.DeathRecipient, var ready: Boolean = false,
        var detail: String = "Iniciando")
    private var session: Session? = null

    private fun displayAllowed(id: Int, uid: Int, target: String): Boolean = runCatching {
        val type = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val manager = type.getMethod("getInstance").invoke(null)
        val info = type.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(manager, id) ?: return@runCatching false
        EmbeddingPolicy.allowsDisplay(id, info.javaClass.getField("ownerUid").getInt(info), uid,
            info.javaClass.getField("name").get(info) as String, target)
    }.getOrDefault(false)

    private fun belongsHere(s: Session, caller: Int): Boolean = caller == s.uid &&
        s.lifetime.isBinderAlive && displayAllowed(s.display, s.uid, s.target)

    @Synchronized override fun startApp(displayId: Int, targetPackage: String, lifetime: IBinder): String {
        if (Build.VERSION.SDK_INT != 29) return "ERROR: este prototipo requiere API 29"
        val caller = Binder.getCallingUid()
        if (!displayAllowed(displayId, caller, targetPackage)) return "ERROR: pantalla ajena, principal o inválida"
        if (!lifetime.isBinderAlive) return "ERROR: panel cerrado"
        if (session != null) return "ERROR: hay una sesión activa; cerrar antes de reintentar"
        val death = IBinder.DeathRecipient { synchronized(this) { clear() } }
        try { lifetime.linkToDeath(death, 0) } catch (_: Exception) { return "ERROR: panel cerrado" }
        val s = Session(displayId, caller, targetPackage, lifetime, death)
        session = s
        try {
            if (!belongsHere(s, caller)) return fail(s, "panel no disponible")
            val process = ProcessBuilder(EmbeddingPolicy.launchCommand(displayId, targetPackage))
                .redirectErrorStream(true).start()
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return fail(s, "el sistema no respondió al inicio")
            }
            val output = process.inputStream.bufferedReader().use { it.readText().take(1500) }
            if (process.exitValue() != 0 || output.contains("Error:") || output.contains("Exception")) {
                return fail(s, output.ifBlank { "inicio rechazado" })
            }
            // An accepted Intent is not proof that a singleTask app stayed on the requested display.
            repeat(20) {
                if (!belongsHere(s, caller)) return fail(s, "panel cerrado durante el inicio")
                if (targetOnDisplay(displayId, targetPackage)) {
                    s.ready = true
                    s.detail = "App en el panel; comprueba imagen, toques y teclado"
                    return "OK: ${s.detail}"
                }
                Thread.sleep(100)
            }
            return fail(s, "la app no quedó en esta pantalla; el firmware puede impedirlo")
        } catch (e: Exception) { return fail(s, e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName) }
    }

    private fun fail(s: Session, reason: String): String {
        s.ready = false
        s.detail = "ERROR: $reason"
        return s.detail
    }

    private fun targetOnDisplay(id: Int, target: String): Boolean = runCatching {
        val type = Class.forName("android.app.ActivityTaskManager")
        val service = type.getMethod("getService").invoke(null)
        val api = Class.forName("android.app.IActivityTaskManager")
        val stacks = api.getMethod("getAllStackInfos").invoke(service) as List<*>
        stacks.any { stack ->
            stack != null && stack.javaClass.getField("displayId").getInt(stack) == id &&
                (stack.javaClass.getField("topActivity").get(stack) as? ComponentName)?.packageName == target
        }
    }.getOrDefault(false)

    private fun inject(event: InputEvent, id: Int): Boolean {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(event, id)
        val type = Class.forName("android.hardware.input.InputManager")
        val manager = type.getMethod("getInstance").invoke(null)
        return type.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            .invoke(manager, event, 0) as Boolean
    }

    @Synchronized override fun touch(displayId: Int, event: MotionEvent) {
        try {
            val s = session ?: return
            if (!s.ready || s.display != displayId || !belongsHere(s, Binder.getCallingUid())) return
            if (!validTouch(event)) return
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            if (!inject(event, displayId)) s.detail = "ERROR: el sistema rechazó el toque"
        } catch (e: Exception) { session?.detail = "ERROR de interacción: ${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}" }
        finally { event.recycle() }
    }

    @Synchronized override fun back(displayId: Int) {
        val s = session ?: return
        if (!s.ready || s.display != displayId || !belongsHere(s, Binder.getCallingUid())) return
        try {
            val time = SystemClock.uptimeMillis()
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(time, time, action, KeyEvent.KEYCODE_BACK, 0, 0,
                    -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
                if (!inject(event, displayId)) s.detail = "ERROR: el sistema rechazó Atrás"
            }
        } catch (e: Exception) { s.detail = "ERROR de Atrás: ${e.javaClass.simpleName}" }
    }

    @Synchronized override fun status(displayId: Int): String {
        val s = session ?: return "Sin sesión"
        if (s.display != displayId || !belongsHere(s, Binder.getCallingUid())) return "Sesión no disponible"
        if (s.ready && !targetOnDisplay(displayId, s.target)) {
            s.ready = false
            s.detail = "ERROR: la app ya no está en el panel"
        }
        return s.detail
    }

    @Synchronized override fun closeSession() {
        val s = session ?: return
        if (Binder.getCallingUid() == s.uid) clear()
    }
    private fun clear() {
        session?.let { runCatching { it.lifetime.unlinkToDeath(it.death, 0) } }
        session = null
    }
    override fun destroy() { synchronized(this) { clear() }; kotlin.system.exitProcess(0) }

    companion object {
        internal fun validTouch(event: MotionEvent): Boolean {
            if (event.pointerCount !in 1..10 || event.actionIndex !in 0 until event.pointerCount) return false
            if (event.actionMasked !in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP)) return false
            return (0 until event.pointerCount).all { event.getX(it).isFinite() && event.getY(it).isFinite() }
        }
    }
}
