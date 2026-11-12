package com.glasscar.launcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
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
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object EmbeddingReport {
    private val details = linkedMapOf<String, String>()
    @Synchronized fun record(target: String, text: String) { details[target] = text.take(1800) }
    @Synchronized fun report(): String = "Apps reales (prototipo API 29):\n" +
        details.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "Sin intentos en esta sesión" }
}

@Composable
fun RealAppPanel(target: String, title: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var status by remember(target) { mutableStateOf("Activa Shizuku y pulsa Iniciar aquí") }
    val controller = remember(target) { RealAppController(context, target) { status = it } }
    SpatialGlassSurface(modifier, cornerRadius = 26.dp) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Txt("$title · app real", 18.sp, modifier = Modifier.weight(1f))
                SmallAction("Iniciar aquí") { controller.start() }
                SmallAction("Atrás") { controller.back() }
                SmallAction("Cerrar") { controller.stop() }
            }
            AndroidView(factory = { TextureView(it).also(controller::attach) },
                modifier = Modifier.weight(1f).fillMaxWidth())
            Txt(status, 14.sp)
            SmallAction("Comprobar interacción") { controller.refreshStatus() }
        }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.dispose() }
    }
}

private class RealAppController(private val context: Context, private val target: String,
    private val update: (String) -> Unit) : TextureView.SurfaceTextureListener {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var view: TextureView? = null
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private var remote: IEmbeddedAppService? = null
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var generation = 0
    private var ready = false
    private var disposed = false
    private var closing = false
    private var connectTimeout: Runnable? = null
    private val lifetime = Binder()
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE && !disposed) tell(if (result == PackageManager.PERMISSION_GRANTED)
            "Autorizado. Pulsa Iniciar aquí" else "Sin autorización de Shizuku")
    }

    init { Shizuku.addRequestPermissionResultListener(permissionListener) }
    private fun tell(text: String) {
        EmbeddingReport.record(target, text)
        main.post { if (!disposed) update(text) }
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
                try { service.touch(current.display.displayId, copy) }
                catch (_: Exception) { stop("Se perdió la conexión de interacción") }
                finally { copy.recycle() }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                    texture.parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
        }
    }

    fun start() {
        if (disposed) return
        if (display != null || closing) { tell("Cierra la sesión actual antes de reintentar"); return }
        if (Build.VERSION.SDK_INT != 29) { tell("Esta prueba está preparada para API 29"); return }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            tell("El firmware no declara soporte para apps en pantallas secundarias"); return
        }
        if (!Shizuku.pingBinder()) { tell("Activa Shizuku mediante ADB o root y vuelve a pulsar Iniciar aquí"); return }
        try {
            if (Shizuku.getVersion() < 12) { tell("Actualiza Shizuku antes de continuar"); return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE)
                tell("Autoriza GlassCar en Shizuku; después pulsa Iniciar aquí")
                return
            }
            val texture = view
            val image = texture?.surfaceTexture
            if (texture == null || image == null || texture.width < 64 || texture.height < 64) {
                tell("Espera a que el panel tenga tamaño y vuelve a intentar"); return
            }
            val component = ComponentName.unflattenFromString(EmbeddingPolicy.components.getValue(target))!!
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(component, 0)
            if (!info.enabled || !info.exported) { tell("La actividad seleccionada no está disponible"); return }
            val width = texture.width; val height = texture.height
            image.setDefaultBufferSize(width, height)
            surface = Surface(image)
            val manager = context.getSystemService(DisplayManager::class.java)
            // Android 10's DESTROY_CONTENT_ON_REMOVAL (1 << 8) avoids migrating guest tasks to display 0.
            display = manager.createVirtualDisplay(EmbeddingPolicy.displayName(android.os.Process.myUid(), target),
                width, height, context.resources.displayMetrics.densityDpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or (1 shl 8))
            val current = display ?: throw IllegalStateException("Sin pantalla virtual")
            val token = ++generation
            val userArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, EmbeddedAppService::class.java.name))
                .daemon(false).processNameSuffix(if (target.contains("maps")) "maps_panel" else "tlink_panel")
                .tag("glasscar-embed-$target").version(BuildConfig.VERSION_CODE)
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
                                tell(result)
                                if (!ready) stop(result)
                            }
                        }
                    }
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    if (!disposed && token == generation) stop("Shizuku se desconectó; vuelve a iniciarlo")
                }
            }
            connection = callback
            tell("Iniciando $target dentro del panel…")
            Shizuku.bindUserService(userArgs, callback)
            connectTimeout = Runnable {
                if (!disposed && token == generation && remote == null) stop("Shizuku no conectó el servicio. Revisa su autorización")
            }.also { main.postDelayed(it, 15000L) }
        } catch (e: Exception) { stop("No se pudo iniciar: ${e.javaClass.simpleName}") }
    }

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
            } }
        }
    }

    fun stop(message: String = "Panel cerrado. Pulsa Iniciar aquí para abrirlo") {
        if (disposed) return
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
            }
        }
        tell(message)
    }
    fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        view?.surfaceTextureListener = null
        view?.setOnTouchListener(null)
        view = null
        worker.shutdown()
    }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (display != null) stop("El panel cambió de tamaño. Pulsa Iniciar aquí")
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { stop(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    companion object { private const val REQUEST_CODE = 8420 }
}

