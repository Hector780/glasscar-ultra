package com.glasscar.launcher

import android.Manifest
import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.KeyEvent
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), LauncherActions {
    private lateinit var location: LocationSource
    private lateinit var media: MediaSource
    private lateinit var canBus: CanBusSource
    private lateinit var preferences: LauncherPreferences
    private lateinit var widgets: WidgetController
    private val weather = WeatherSource()
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val showDrawer = mutableStateOf(false)
    private val permissionVersion = mutableIntStateOf(0)
    private val carPlayRequest = mutableIntStateOf(0)
    private val volumeKeyEvent = mutableIntStateOf(0)
    private var askedWriteSettings = false
    private var projectionReceiverRegistered = false
    private val projectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ProjectionCalls.vendorStatus(intent.action, intent.getStringExtra("status"))
        }
    }
    // 4.7: TWService attaches its reverse flag to this broadcast on every change (class x, what 0x9f1c).
    // Registered for the whole Activity life so the exit from reverse is seen while the OEM camera covers GlassCar.
    private var reverseReceiverRegistered = false
    private val reverseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra(ReverseProtocol.BROADCAST_EXTRA)) {
                ReverseSignal.report(ReverseSource.TW_BROADCAST, intent.getIntExtra(ReverseProtocol.BROADCAST_EXTRA, -1))
            }
        }
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionVersion.intValue++
        if (::location.isInitialized) location.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersive()
        preferences = LauncherPreferences(this)
        widgets = WidgetController(this, preferences).also { it.restore(savedInstanceState) }
        location = LocationSource(this)
        media = MediaSource(this)
        canBus = CanBusSource(this)
        setContent {
            LauncherRoot(apps.value, location.state, media.state, canBus.state, weather,
                showDrawer.value, { showDrawer.value = !showDrawer.value }, { showDrawer.value = false }, this,
                preferences = preferences, permissionVersion = permissionVersion.intValue,
                carPlayRequest = carPlayRequest.intValue, volumeKeyEvent = volumeKeyEvent.intValue)
        }
        permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        val reverseFilter = IntentFilter(ReverseProtocol.BROADCAST_MUTE)
        reverseFilter.addAction(ReverseProtocol.BROADCAST_UNMUTE)
        runCatching {
            ContextCompat.registerReceiver(this, reverseReceiver, reverseFilter, ContextCompat.RECEIVER_EXPORTED)
            reverseReceiverRegistered = true
        }
    }

    override fun onDestroy() {
        if (reverseReceiverRegistered) {
            runCatching { unregisterReceiver(reverseReceiver) }
            reverseReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        location.start(); media.start(); canBus.start(); TopwayAudio.start(this)
        if (BuildConfig.REAL_APP_EMBEDDING && !projectionReceiverRegistered) {
            ContextCompat.registerReceiver(this, projectionReceiver, IntentFilter(TLINK_PACKAGE), ContextCompat.RECEIVER_EXPORTED)
            projectionReceiverRegistered = true
        }
        runCatching { widgets.host.startListening() }
    }
    override fun onStop() {
        location.stop(); media.stop(); canBus.stop(); TopwayAudio.stop()
        if (projectionReceiverRegistered) { unregisterReceiver(projectionReceiver); projectionReceiverRegistered = false }
        ProjectionCalls.clearVendor()
        widgets.host.stopListening(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) { widgets.save(outState); super.onSaveInstanceState(outState) }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        location.start(); media.start(); canBus.start()
        permissionVersion.intValue++
        lifecycleScope.launch {
            apps.value = withContext(Dispatchers.Default) { AppsRepository.load(this@MainActivity) }
            val recents = withContext(Dispatchers.IO) { readSystemRecents(this@MainActivity, apps.value) }
            if (recents.isNotEmpty()) preferences.recordRecent(recents)
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterImmersive() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); showDrawer.value = false }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return super.onKeyDown(keyCode, event)
        }
        volumeKeyEvent.intValue++
        // On TS10 the MCU owns volume. Do not send a second command for its key event.
        if (preferences.state.value.nativeAudio) return super.onKeyDown(keyCode, event)
        getSystemService(AudioManager::class.java)?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!preferences.state.value.nativeAudio && keyCode in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun enterImmersive() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun open(app: AppEntry) {
        if (routesToCarPlay(app.packageName, BuildConfig.REAL_APP_EMBEDDING)) {
            // Tlink lives only inside the CarPlay tab: do not add it to recents/dock.
            showDrawer.value = false; carPlayRequest.intValue++; return
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (safeStart(intent, app.label)) preferences.record(app)
    }
    override fun uninstall(app: AppEntry) {
        // Android owns the confirmation and protects system packages. Never silently remove an app.
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
        if (!safeStart(intent, "Desinstalar ${app.label}")) {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName, null)), "Información de ${app.label}")
        }
    }
    override fun openRearCamera() {
        safeStart(RearCameraPolicy.intent(), "Cámara trasera original")
    }
    override fun open(shortcut: Shortcut, apps: List<AppEntry>) {
        val target = AppsRepository.find(apps, shortcut)
        when {
            target != null -> open(target)
            shortcut.fallbackAction != null -> safeStart(Intent(shortcut.fallbackAction), shortcut.title)
            else -> toast("Selecciona una aplicación para ${shortcut.title}")
        }
    }
    override fun openSlot(slot: String, shortcut: Shortcut, apps: List<AppEntry>) {
        val target = resolveApp(apps, preferences.state.value, slot, shortcut)
        when {
            target != null -> open(target)
            preferences.state.value.assignments.containsKey(slot) -> toast("La aplicación elegida ya no está disponible. Mantén presionado para cambiarla.")
            else -> open(shortcut, apps)
        }
    }
    override fun openPackage(packageName: String) {
        if (routesToCarPlay(packageName, BuildConfig.REAL_APP_EMBEDDING)) {
            showDrawer.value = false; carPlayRequest.intValue++; return
        }
        apps.value.firstOrNull { it.packageName == packageName }?.let { open(it); return }
        packageManager.getLaunchIntentForPackage(packageName)?.let { safeStart(it, packageName) }
    }
    override fun openNotificationAccess() {
        toast("Activa GlassCar para mostrar música e indicaciones de navegación")
        safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Acceso a notificaciones")
    }
    override fun setVolume(fraction: Float) {
        if (preferences.state.value.nativeAudio) {
            TopwayAudio.controller.setFraction(fraction)
            return
        }
        runCatching { HeadUnitControls.setAndroidVolume(this, fraction) }
            .onFailure { toast("Usa el control de volumen del estéreo") }
    }
    override fun openNativeControls() {
        if (!HeadUnitControls.openNativePanel(this)) toast("Abre el panel superior del estéreo para ajustar sus controles")
    }
    override fun setBrightness(fraction: Float) {
        // 4.7: on the TS10 the backlight belongs to the MCU (TW 516). Settings.System does not move the panel,
        // so it is never written there as if it were the solution.
        if (TopwayAudio.controller.state.connected) {
            TopwayAudio.controller.setBrightnessFraction(fraction)
            return
        }
        if (HeadUnitControls.setBrightness(this, window, fraction)) return
        if (!Settings.System.canWrite(this) && !askedWriteSettings) {
            askedWriteSettings = true
            toast("Permite cambiar el brillo del sistema")
            safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")), "Permiso de brillo")
        } else openNativeControls()
    }
    override fun openUsageAccess() { safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), "Acceso de uso") }
    override fun findWeatherApp() {
        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=weather&c=apps")), "Aplicaciones de clima")
    }
    override fun requestCamera() { permissionRequest.launch(arrayOf(Manifest.permission.CAMERA)) }
    override fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) permissionRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        else safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth")
    }
    override fun chooseWidget(slot: String) = widgets.choose(slot)
    override fun removeWidget(slot: String) = widgets.remove(slot)
    override fun widgetHost(): AppWidgetHost = widgets.host
    override fun playPause() = media.playPause()
    override fun next() = media.next()
    override fun previous() = media.previous()

    private fun safeStart(intent: Intent, label: String): Boolean = runCatching { startActivity(intent); true }
        .getOrElse { toast("No se pudo abrir $label"); false }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}

