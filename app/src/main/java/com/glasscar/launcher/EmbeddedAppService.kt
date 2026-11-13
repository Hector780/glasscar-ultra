package com.glasscar.launcher

import android.content.ComponentName
import android.hardware.usb.UsbDevice
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.Display
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
        val lifetime: IBinder, val death: IBinder.DeathRecipient, @Volatile var ready: Boolean = false,
        @Volatile var detail: String = "Iniciando", var usbDetail: String = "", var launches: Int = 0, var moves: Int = 0)
    @Volatile private var session: Session? = null

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
            s.usbDetail = prepareUsb(targetPackage, caller / 100_000)
            // A task left on the main screen would be brought forward there by am start: pull it in first.
            val attemptedStacks = mutableSetOf<Int>()
            val pulled = pullTargetHere(s, attemptedStacks)
            val launchedAt = SystemClock.uptimeMillis()
            val output = run(EmbeddingPolicy.launchCommand(displayId, targetPackage))
                ?: return fail(s, "el sistema no respondió al inicio")
            s.launches++
            if (output.startsWith("EXIT") || output.contains("Error:") || output.contains("Exception")) {
                return fail(s, output.ifBlank { "inicio rechazado" })
            }
            // An accepted Intent is not proof that a singleTask app stayed on the requested display.
            repeat(90) {
                if (!belongsHere(s, caller)) return fail(s, "panel cerrado durante el inicio")
                val moved = pullTargetHere(s, attemptedStacks)
                val settled = SystemClock.uptimeMillis() - launchedAt >= EmbeddingPolicy.settleMillis(targetPackage)
                if (moved == 0 && settled && targetOnDisplay(displayId, targetPackage)) {
                    s.ready = true
                    s.detail = listOf("App en el panel; comprueba imagen, toques y teclado",
                        if (pulled > 0) "traída desde la pantalla principal" else "")
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    return "OK: ${sessionDetail(s)}"
                }
                Thread.sleep(100)
            }
            return fail(s, "la app no quedó en esta pantalla; el firmware puede impedirlo")
        } catch (e: Exception) { return fail(s, e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName) }
    }

    /** Runs a fixed argument list (never a shell string). Returns output, "EXIT n" on failure or null on timeout. */
    private fun run(command: List<String>): String? {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); return null }
        val output = process.inputStream.bufferedReader().use { it.readText().take(1500) }
        return if (process.exitValue() != 0) "EXIT ${process.exitValue()} $output" else output
    }

    private fun stackInfos(): List<Any> = runCatching {
        val type = Class.forName("android.app.ActivityTaskManager")
        val service = type.getMethod("getService").invoke(null)
        val api = Class.forName("android.app.IActivityTaskManager")
        (api.getMethod("getAllStackInfos").invoke(service) as List<*>).filterNotNull()
    }.getOrDefault(emptyList())

    /** Stacks that belong only to [target] and sit on the main screen. */
    private fun mainScreenStacks(target: String): List<Int> = stackInfos().mapNotNull { stack ->
        runCatching {
            val type = stack.javaClass
            if (type.getField("displayId").getInt(stack) != Display.DEFAULT_DISPLAY) return@runCatching null
            val top = (type.getField("topActivity").get(stack) as? ComponentName)?.packageName
            val names = (type.getField("taskNames").get(stack) as? Array<*>)?.mapNotNull { it as? String }.orEmpty()
            if (EmbeddingPolicy.ownsStack(target, top, names)) type.getField("stackId").getInt(stack) else null
        }.getOrNull()
    }

    /** 4.6: moves the target's own tasks from display 0 back into its panel (e.g. a call screen or DVR hand-off). */
    private fun pullTargetHere(s: Session, attempted: MutableSet<Int>? = null): Int = mainScreenStacks(s.target)
        .filter { attempted?.add(it) != false }.count { stack ->
        runCatching { run(EmbeddingPolicy.moveStackCommand(stack, s.display)) }.getOrNull()
            ?.let { !it.startsWith("EXIT") && !it.contains("Exception") && !it.contains("Error:") } == true
    }.also { s.moves += it }

    /**
     * Handle the known USB attach flow. The user's missing DVR message has not yet been captured;
     * granting permission/default is an interoperability hypothesis, not proof of working video.
     */
    private fun prepareUsb(target: String, userId: Int): String {
        val wanted = EmbeddingPolicy.usbDevices[target] ?: return ""
        return runCatching {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "usb") as IBinder
            val usb = Class.forName("android.hardware.usb.IUsbManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val api = Class.forName("android.hardware.usb.IUsbManager")
            val devices = Bundle().apply { classLoader = UsbDevice::class.java.classLoader }
            api.getMethod("getDeviceList", Bundle::class.java).invoke(usb, devices)
            val pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null)!!
            val uid = Class.forName("android.content.pm.IPackageManager").getMethod("getPackageUid", String::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType).invoke(pm, target, 0, userId) as Int
            require(uid >= 0) { "DVR no instalada para el usuario" }
            var count = 0
            var defaults = 0
            for (key in devices.keySet()) {
                @Suppress("DEPRECATION") val device = devices.getParcelable<UsbDevice>(key) ?: continue
                if ((device.vendorId to device.productId) !in wanted) continue
                api.getMethod("grantDevicePermission", UsbDevice::class.java, Int::class.javaPrimitiveType)
                    .invoke(usb, device, uid)
                runCatching {
                    api.getMethod("setDevicePackage", UsbDevice::class.java, String::class.java, Int::class.javaPrimitiveType)
                        .invoke(usb, device, target, userId)
                }.onSuccess { defaults++ }
                count++
            }
            if (count == 0) "USB: cámara DVR no detectada" else
                "USB: permiso DVR concedido · predeterminada=$defaults/$count"
        }.getOrElse { "USB: ${((it as? java.lang.reflect.InvocationTargetException)?.targetException ?: it).javaClass.simpleName}" }
    }

    private fun fail(s: Session, reason: String): String {
        s.ready = false
        s.detail = "ERROR: $reason"
        return sessionDetail(s)
    }

    private fun targetOnDisplay(id: Int, target: String): Boolean = runCatching {
        val type = Class.forName("android.app.ActivityTaskManager")
        val service = type.getMethod("getService").invoke(null)
        val api = Class.forName("android.app.IActivityTaskManager")
        val stacks = api.getMethod("getAllStackInfos").invoke(service) as List<*>
        stacks.any { stack ->
            stack != null && stack.javaClass.getField("displayId").getInt(stack) == id &&
                ((stack.javaClass.getField("topActivity").get(stack) as? ComponentName)?.packageName == target ||
                    (stack.javaClass.getField("taskNames").get(stack) as? Array<*>)
                        ?.any { (it as? String)?.startsWith("$target/") == true } == true)
        }
    }.getOrDefault(false)

    private fun inject(event: InputEvent, id: Int): Boolean {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(event, id)
        val type = Class.forName("android.hardware.input.InputManager")
        val manager = type.getMethod("getInstance").invoke(null)
        return type.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            .invoke(manager, event, 0) as Boolean
    }

    // A slow status/recovery command must not hold input behind the service's monitor.
    override fun touch(displayId: Int, event: MotionEvent) {
        try {
            val s = session ?: return
            if (!s.ready || s.display != displayId || !belongsHere(s, Binder.getCallingUid())) return
            if (!validTouch(event)) return
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            if (!inject(event, displayId)) s.detail = "ERROR: el sistema rechazó el toque"
        } catch (e: Exception) { session?.detail = "ERROR de interacción: ${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}" }
        finally { event.recycle() }
    }

    override fun back(displayId: Int) {
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
        // With the launcher running, an empty global stack inventory is not evidence that the guest died.
        if (stackInfos().isEmpty()) return "${sessionDetail(s)} · consulta de tareas no disponible"
        if (s.ready) {
            // 4.6 self-healing: bring back tasks the firmware or the app moved to the main screen.
            if (pullTargetHere(s) > 0) {
                if (!targetOnDisplay(displayId, s.target)) return fail(s, "movimiento no confirmado en el panel")
                s.detail = "App recuperada en el panel"
            }
            else if (!targetOnDisplay(displayId, s.target)) {
                val relaunch = runCatching { run(EmbeddingPolicy.launchCommand(displayId, s.target)) }.getOrNull()
                s.launches++
                if (relaunch == null || relaunch.startsWith("EXIT") || relaunch.contains("Error:") ||
                    relaunch.contains("Exception") || !targetOnDisplay(displayId, s.target)) {
                    s.ready = false
                    s.detail = "ERROR: la app ya no está en el panel"
                } else s.detail = "App reabierta en el panel"
            }
        }
        return sessionDetail(s)
    }

    private fun sessionDetail(s: Session) = listOf(s.detail, s.usbDetail,
        "iniciosApp=${s.launches} · movimientos=${s.moves}").filter { it.isNotBlank() }.joinToString(" · ")

    @Synchronized override fun closeSession() {
        val s = session ?: return
        if (Binder.getCallingUid() == s.uid) clear()
    }
    private fun clear() {
        session?.let { it.ready = false; runCatching { it.lifetime.unlinkToDeath(it.death, 0) } }
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
