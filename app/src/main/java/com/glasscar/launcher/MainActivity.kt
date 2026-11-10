package com.glasscar.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), LauncherActions {

    private lateinit var location: LocationSource
    private lateinit var media: MediaSource
    private lateinit var canBus: CanBusSource
    private val weather = WeatherSource()
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val showDrawer = mutableStateOf(false)
    private var askedWriteSettings = false

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { location.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Evitar que la pantalla del estéreo se apague mientras conduces
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersive()

        location = LocationSource(this)
        media = MediaSource(this)
        canBus = CanBusSource(this)

        setContent {
            LauncherRoot(
                apps = apps.value,
                driveFlow = location.state,
                mediaFlow = media.state,
                canFlow = canBus.state,
                weatherSource = weather,
                showDrawer = showDrawer.value,
                onToggleDrawer = { showDrawer.value = !showDrawer.value },
                onCloseDrawer = { showDrawer.value = false },
                actions = this
            )
        }

        permissionRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        location.start()
        media.start()
        canBus.start()
        lifecycleScope.launch {
            apps.value = withContext(Dispatchers.Default) { AppsRepository.load(this@MainActivity) }
        }
    }

    override fun onPause() {
        super.onPause()
        location.stop()
        media.stop()
        canBus.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showDrawer.value = false
    }

    /** Pantalla completa: oculta barras de estado y navegación; aparecen temporalmente al deslizar. */
    private fun enterImmersive() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------------- LauncherActions ----------------

    override fun open(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(intent, app.label)
    }

    override fun open(shortcut: Shortcut, apps: List<AppEntry>) {
        val target = AppsRepository.find(apps, shortcut)
        when {
            target != null -> open(target)
            shortcut.fallbackAction != null ->
                safeStart(Intent(shortcut.fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), shortcut.title)
            else -> toast("No encontré una app de ${shortcut.title}")
        }
    }

    override fun openPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) safeStart(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), packageName)
    }

    override fun openNotificationAccess() {
        toast("Activa GlassCar para que pueda mostrar tu música")
        safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Ajustes")
    }

    override fun setVolume(fraction: Float) {
        val am = getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt(), 0) }
    }

    override fun setBrightness(fraction: Float) {
        val value = fraction.coerceIn(0.02f, 1f)
        if (Settings.System.canWrite(this)) {
            runCatching {
                Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255).roundToInt())
            }
            setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        } else {
            setWindowBrightness(value)
            if (!askedWriteSettings) {
                askedWriteSettings = true
                toast("Permite a GlassCar cambiar el brillo del sistema")
                safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")), "Ajustes")
            }
        }
    }

    override fun playPause() = media.playPause()
    override fun next() = media.next()
    override fun previous() = media.previous()

    // ---------------- Utilidades ----------------

    private fun setWindowBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    private fun safeStart(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No se pudo abrir $label")
        } catch (e: SecurityException) {
            toast("No se pudo abrir $label")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
