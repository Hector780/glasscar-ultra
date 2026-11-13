package com.glasscar.launcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

internal class EmbeddedSessionStore(private val context: Context) {
    private val sessions = linkedMapOf<String, RealAppController>()
    private var active = false
    fun session(target: String) = sessions.getOrPut(target) { RealAppController(context, target).also { it.setActive(active) } }
    fun setActive(value: Boolean) { active = value; sessions.values.forEach { it.setActive(value) } }
    fun dispose() { sessions.values.forEach { it.dispose() }; sessions.clear() }
}

internal val LocalEmbeddedSessions = staticCompositionLocalOf<EmbeddedSessionStore?> { null }

object EmbeddingReport {
    private val details = linkedMapOf<String, String>()
    @Synchronized fun record(target: String, text: String) { details[target] = text.take(1800) }
    @Synchronized fun report(): String = "Apps reales (prototipo API 29):\n" +
        details.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "Sin intentos en esta sesión" }
}

@Composable
fun RealAppPanel(target: String, title: String, modifier: Modifier,
    expanded: Boolean = false, onExpand: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = LocalEmbeddedSessions.current
    val controller = remember(target, store) { store?.session(target) ?: RealAppController(context, target) }
    EmbeddedPanelFrame(title, controller.status, modifier, controller::start, expanded, onExpand) {
        AndroidView(factory = { TextureView(it).also(controller::attach) }, modifier = Modifier.fillMaxSize(),
            onRelease = controller::detach)
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.setActive(true)
                Lifecycle.Event.ON_STOP -> controller.setActive(false)
                else -> Unit
            }
        }
        if (store == null) {
            lifecycle.addObserver(observer)
            controller.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        onDispose { lifecycle.removeObserver(observer); if (store == null) controller.dispose() }
    }
}

internal data class EmbeddedPanelStatus(val message: String = "Conectando…", val running: Boolean = false,
    val retry: Boolean = false)

/** The guest owns the full panel. Connection help appears only until it is running. */
@Composable
internal fun EmbeddedPanelFrame(title: String, status: EmbeddedPanelStatus, modifier: Modifier,
    onRetry: () -> Unit, expanded: Boolean = false, onExpand: (() -> Unit)? = null,
    content: @Composable () -> Unit) {
    Box(modifier.background(Color.Black)
        .semantics { contentDescription = "Panel de $title" }) {
        content()
        if (!status.running) {
            Column(Modifier.fillMaxSize().background(LocalBackdrop.current.palette.abyss).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Txt(title, 20.sp)
                Spacer(Modifier.height(8.dp))
                Txt(status.message, 15.sp)
                if (status.retry) SmallAction("Reintentar", onRetry)
            }
        }
        if (onExpand != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(44.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xD9222936))
            .semantics { contentDescription = if (expanded) "Reducir mapa" else "Ampliar mapa" }
            .bounceClick(onClick = onExpand), contentAlignment = Alignment.Center) {
            Txt(if (expanded) "↙" else "⛶", 24.sp, color = Color.White)
        }
    }
}

internal class RealAppController(private val context: Context, private val target: String) : TextureView.SurfaceTextureListener {
    var status by mutableStateOf(EmbeddedPanelStatus())
        private set
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var view: TextureView? = null
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private var retainedTexture: SurfaceTexture? = null
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var launches = 0
    private var remote: IEmbeddedAppService? = null
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var generation = 0
    private var ready = false
    private var disposed = false
    private var closing = false
    private var active = false
    private var restartAfterClose = false
    private var connectTimeout: Runnable? = null
    private var watchdog: Runnable? = null
    private val lifetime = Binder()
    // A new launcher instance must not reuse a service whose previous host is still closing.
    private val serviceTag = "glasscar-embed-$target-${java.util.UUID.randomUUID()}"
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == requestCode && !disposed) main.post {
            if (!disposed && active) {
                if (result == PackageManager.PERMISSION_GRANTED) start()
                else tell("Autoriza GlassCar en Shizuku para abrir esta app")
            }
        }
    }

    private val requestCode = 8420 + EmbeddingPolicy.components.keys.indexOf(target)
    init { Shizuku.addRequestPermissionResultListener(permissionListener) }
    private fun tell(text: String, retry: Boolean = true) {
        reportSession(text)
        val state = EmbeddedPanelStatus(text, running = ready, retry = retry)
        main.post { if (!disposed) status = state }
    }

    private fun reportSession(text: String) {
        EmbeddingReport.record(target, "$text · inicios=$launches · display=${display?.display?.displayId ?: -1}" +
            " · buffer=${bufferWidth}x$bufferHeight · superficie=${retainedTexture?.isReleased == false}")
    }

    fun setActive(value: Boolean) {
        if (disposed || active == value) return
        active = value
        // Retain the guest task across tab changes and temporary window focus loss.
        if (value) { start(); if (ready) scheduleWatchdog() } else cancelWatchdog()
    }

    fun attach(texture: TextureView) {
        view = texture
        texture.surfaceTextureListener = this
        texture.setOnTouchListener { _, event ->
            val current = display
            val service = remote
            if (!ready || current == null || service == null) false else {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) texture.parent?.requestDisallowInterceptTouchEvent(true)
                val copy = MotionEvent.obtain(event)
                copy.transform(Matrix().apply { setScale(bufferWidth.toFloat() / texture.width.coerceAtLeast(1),
                    bufferHeight.toFloat() / texture.height.coerceAtLeast(1)) })
                try { service.touch(current.display.displayId, copy) }
                catch (_: Exception) { stop("Se perdió la conexión de interacción") }
                finally { copy.recycle() }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                    texture.parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
        }
        if (texture.isAvailable) start()
    }

    fun detach(texture: TextureView) {
        texture.setOnTouchListener(null)
        if (view === texture) view = null
        // Keep the listener until Android detaches the view: returning false retains its buffer.
    }

    fun start() {
        if (disposed || !active || display != null) return
        if (closing) { restartAfterClose = true; return }
        val texture = view
        val image = texture?.surfaceTexture
        if (texture == null || image == null || texture.width < 64 || texture.height < 64) return
        if (Build.VERSION.SDK_INT != 29) { tell("La integración requiere el Android 10 del estéreo"); return }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            tell("El firmware no declara soporte para apps en pantallas secundarias"); return
        }
        if (!Shizuku.pingBinder()) { tell("Abre Shizuku, inicia su servicio y vuelve a intentar"); return }
        try {
            if (Shizuku.getVersion() < 12) { tell("Actualiza Shizuku antes de continuar"); return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
                tell("Autoriza GlassCar en Shizuku para abrir esta app")
                return
            }
            val component = ComponentName.unflattenFromString(EmbeddingPolicy.components.getValue(target))!!
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(component, 0)
            if (!info.enabled || !info.exported) { tell("La actividad seleccionada no está disponible"); return }
            val width = texture.width; val height = texture.height
            image.setDefaultBufferSize(width, height)
            retainedTexture = image
            bufferWidth = width; bufferHeight = height
            surface = Surface(image)
            val manager = context.getSystemService(DisplayManager::class.java)
            // Android 10's DESTROY_CONTENT_ON_REMOVAL (1 << 8) avoids migrating guest tasks to display 0.
            display = createPanelDisplay(manager, width, height)
            val current = display ?: throw IllegalStateException("Sin pantalla virtual")
            launches++
            val token = ++generation
            val userArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, EmbeddedAppService::class.java.name))
                .daemon(false).processNameSuffix(when (target) {
                    "com.google.android.apps.maps" -> "maps_panel"
                    "com.williexing.android.apps.xcdvr1" -> "dvr_panel"
                    else -> "tlink_panel"
                })
                .tag(serviceTag).version(BuildConfig.VERSION_CODE)
            args = userArgs
            val callback = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (disposed || token != generation) return
                    connectTimeout?.let(main::removeCallbacks); connectTimeout = null
                    val service = IEmbeddedAppService.Stub.asInterface(binder)
                    remote = service
                    worker.execute {
                        val result = runCatching { service.startApp(current.display.displayId, target, lifetime) }
                            .getOrElse { "ERROR: ${it.javaClass.simpleName}" }
                        main.post {
                            if (!disposed && token == generation) {
                                ready = result.startsWith("OK:")
                                tell(result, retry = !ready)
                                if (!ready) stop(result) else scheduleWatchdog()
                            }
                        }
                    }
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    if (!disposed && token == generation) stop("Shizuku se desconectó; vuelve a iniciarlo")
                }
            }
            connection = callback
            tell("Conectando…", retry = false)
            Shizuku.bindUserService(userArgs, callback)
            connectTimeout = Runnable {
                if (!disposed && token == generation && remote == null) stop("Shizuku no conectó el servicio. Revisa su autorización")
            }.also { main.postDelayed(it, 15000L) }
        } catch (e: Exception) { stop("No se pudo iniciar: ${e.javaClass.simpleName}") }
    }

    // AOSP android-10.0.0_r1 DisplayManager defines this hidden flag as 1 << 8.
    // start() permits API 29 only. Keep it: removal must destroy guest content, not move it to display 0.
    @SuppressLint("WrongConstant")
    private fun createPanelDisplay(manager: DisplayManager, width: Int, height: Int): VirtualDisplay? =
        manager.createVirtualDisplay(EmbeddingPolicy.displayName(android.os.Process.myUid(), target),
            width, height, context.resources.displayMetrics.densityDpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or (1 shl 8))

    fun back() {
        val d = display ?: return
        if (!ready) return
        runCatching { remote?.back(d.display.displayId) }.onFailure { stop("Se perdió la conexión") }
    }
    fun refreshStatus() {
        val d = display ?: run { tell("Sin app incrustada activa"); return }
        val service = remote ?: return
        val token = generation
        worker.execute {
            val message = runCatching { service.status(d.display.displayId) }.getOrDefault("No se pudo consultar la sesión")
            main.post { if (!disposed && token == generation) {
                if (message.startsWith("ERROR")) stop(message) else tell(message)
                if (active && ready) scheduleWatchdog()
            } }
        }
    }

    /** 4.6: every 2.5 s the Shizuku service pulls the guest back if it left the panel (calls, DVR hand-off). */
    private fun scheduleWatchdog() {
        cancelWatchdog()
        watchdog = object : Runnable {
            override fun run() {
                if (disposed || !active || !ready || display == null) return
                refreshStatus()
                // The completion schedules the next check. Slow Binder calls cannot pile up.
            }
        }.also { main.postDelayed(it, WATCHDOG_MS) }
    }
    private fun cancelWatchdog() { watchdog?.let(main::removeCallbacks); watchdog = null }

    fun stop(message: String = "Conectando…") {
        if (disposed) return
        cancelWatchdog()
        if (closing) { tell(message); return }
        connectTimeout?.let(main::removeCallbacks); connectTimeout = null
        generation++; ready = false
        val oldDisplay = display; display = null
        val oldSurface = surface; surface = null
        val service = remote; remote = null
        val oldArgs = args; args = null
        val oldConnection = connection; connection = null
        runCatching { oldDisplay?.surface = null }
        closing = true
        // Serialize with launch: do not destroy the target display underneath an am start in flight.
        worker.execute {
            runCatching { service?.closeSession() }
            main.post {
                if (oldArgs != null && oldConnection != null) runCatching { Shizuku.unbindUserService(oldArgs, oldConnection, true) }
                runCatching { oldDisplay?.release() }; oldSurface?.release()
                closing = false
                if (restartAfterClose && active && !disposed) {
                    restartAfterClose = false
                    start()
                }
            }
        }
        tell(message)
    }
    fun dispose() {
        if (disposed) return
        active = false
        restartAfterClose = false
        stop()
        disposed = true
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        retainedTexture?.let { runCatching { it.release() } }; retainedTexture = null
        view?.surfaceTextureListener = null
        view?.setOnTouchListener(null)
        view = null
        worker.shutdown()
    }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val previous = retainedTexture
        if (previous != null && previous !== surface && !previous.isReleased) view?.setSurfaceTexture(previous)
        else retainedTexture = surface
        start()
        reportSession(if (ready) "Sesión conservada" else status.message)
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (width < 64 || height < 64) return
        // Scale the retained buffer and map touch coordinates, avoiding guest activity recreation.
        if (display == null) start()
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (disposed) return true
        retainedTexture = surface
        return false
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    private companion object { const val WATCHDOG_MS = 2_500L }
}

