package com.glasscar.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun availableCameras(context: Context): List<String> = runCatching {
    context.getSystemService(CameraManager::class.java).cameraIdList.toList()
}.getOrDefault(emptyList())

/** Only cameras actually exposed by Android are opened. Vendor-only DVR apps use their widget/shortcut. */
private class CameraSession(private val context: Context, private val view: TextureView, private val id: String,
    private val onReady: () -> Unit, private val onError: (String) -> Unit) : TextureView.SurfaceTextureListener {
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private val thread = HandlerThread("GlassCarCamera").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var running = false
    @Volatile private var opening = false
    @Volatile private var generation = 0
    private var hasFrame = false
    private val main = Handler(context.mainLooper)
    private var timeout: Runnable? = null
    init { view.surfaceTextureListener = this }

    fun resume() {
        if (running) return
        running = true
        hasFrame = false
        val watch = Runnable { if (running && !hasFrame) fail("La cámara no entregó imagen. Revisa el DVR o selecciona otra cámara.") }
        timeout = watch
        main.postDelayed(watch, 5000L)
        if (view.isAvailable) open()
    }
    fun pause() {
        timeout?.let(main::removeCallbacks); timeout = null
        running = false; opening = false; generation++
        session?.close(); session = null
        camera?.close(); camera = null
        surface?.release(); surface = null
    }
    fun dispose() { pause(); view.surfaceTextureListener = null; thread.quitSafely() }

    @SuppressLint("MissingPermission")
    private fun open() {
        if (!running || opening || camera != null || !view.isAvailable) return
        opening = true
        val token = ++generation
        val manager = context.getSystemService(CameraManager::class.java)
        runCatching {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (!running || token != generation) { device.close(); return }
                    opening = false
                    camera = device
                    runCatching {
                    val texture = view.surfaceTexture ?: run { device.close(); return }
                    val sizes = manager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(SurfaceTexture::class.java)
                    val size = sizes?.filter { it.width <= 1280 }?.maxByOrNull { it.width * it.height } ?: sizes?.firstOrNull()
                    size?.let { texture.setDefaultBufferSize(it.width, it.height) }
                    val output = Surface(texture); surface = output
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(output); set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    }
                    @Suppress("DEPRECATION")
                    device.createCaptureSession(listOf(output), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(configured: CameraCaptureSession) {
                            if (!running || token != generation) { configured.close(); return }
                            session = configured
                            runCatching { configured.setRepeatingRequest(request.build(), null, handler) }
                                .onFailure { if (token == generation) fail("La cámara no permite vista previa") }
                        }
                        override fun onConfigureFailed(configured: CameraCaptureSession) {
                            configured.close()
                            if (token == generation) fail("No se pudo iniciar la cámara")
                        }
                    }, handler)
                    }.onFailure { if (token == generation) fail("La cámara no permite vista previa") }
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); if (token == generation) fail("Cámara desconectada") }
                override fun onError(device: CameraDevice, error: Int) { device.close(); if (token == generation) fail("Cámara ocupada o no disponible") }
            }, handler)
        }.onFailure { if (token == generation) fail("No se puede acceder a esta cámara") }
    }
    private fun fail(message: String) { Handler(context.mainLooper).post { onError(message) }; pause() }
    override fun onSurfaceTextureAvailable(t: SurfaceTexture, width: Int, height: Int) { if (running) open() }
    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(t: SurfaceTexture) {
        if (running && !hasFrame) {
            hasFrame = true
            timeout?.let(main::removeCallbacks); timeout = null
            onReady()
        }
    }
    override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean { val active = running; pause(); running = active; return true }
}

@Composable
fun CameraPreview(cameraId: String, modifier: Modifier, onReady: () -> Unit = {}, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var session by remember(cameraId) { mutableStateOf<CameraSession?>(null) }
    AndroidView(
        factory = { TextureView(it).also { view ->
            session = CameraSession(it, view, cameraId, onReady, onError)
            if (ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session?.resume()
        } },
        modifier = modifier
    )
    val activeSession = session
    DisposableEffect(activeSession, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_RESUME -> activeSession?.resume(); Lifecycle.Event.ON_PAUSE -> activeSession?.pause(); else -> Unit }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); activeSession?.dispose() }
    }
}

