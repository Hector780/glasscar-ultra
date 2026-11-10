package com.glasscar.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * true = la interfaz muestra datos simulados (velocidad, música, clima) para probar el diseño.
 * Déjalo en false en el auto: un velocímetro con datos falsos es peligroso.
 */
const val DEMO_MODE = false

// =====================================================================
//  Apps instaladas y accesos (Optimizado con paquetes nativos TS10 / Topway / FYT)
// =====================================================================

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap,
    val iconLight: ImageBitmap? = null,
    val iconDark: ImageBitmap? = null
)

data class Shortcut(
    val title: String,
    val glyph: Glyph,
    val packages: List<String>,
    val keywords: List<String>,
    val fallbackAction: String? = null,
    val colors: Pair<Color, Color> = Squircle.Settings,
    val iconResName: String? = null
)

/** Dock lateral: Mapas, Música, Teléfono, Telemetría/OBD. */
val DockShortcuts = listOf(
    Shortcut(
        "Mapas", Glyph.Map,
        listOf("com.google.android.apps.maps", "com.waze"),
        listOf("maps", "mapa", "navi"),
        colors = Squircle.Maps, iconResName = "icon_maps"
    ),
    Shortcut(
        "Música", Glyph.Note,
        listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.ts.music", "com.tw.music"),
        listOf("music", "música", "musica", "spotify"),
        colors = Squircle.Music, iconResName = "icon_music"
    ),
    Shortcut(
        "Teléfono", Glyph.Phone,
        listOf("com.ts.bt", "com.tw.bt", "com.syu.bt", "com.android.dialer", "com.google.android.dialer"),
        listOf("phone", "teléfono", "telefono", "bluetooth", "dial", "bt"),
        colors = Squircle.Phone, iconResName = "icon_phone"
    ),
    Shortcut(
        "OBD", Glyph.Gauge,
        listOf("org.prowl.torque", "org.prowl.torquefree", "com.ovz.carscanner", "com.ts.can"),
        listOf("obd", "torque", "scanner", "telemetr", "car info", "vehículo"),
        colors = Squircle.Telemetry, iconResName = "icon_obd"
    )
)


/**
 * Catálogo extendido de 26 íconos de macOSicons.com -> paquetes y palabras clave de Android / TS10.
 * Sirve tanto para el Dock y Accesos Rápidos como para reemplazar automáticamente los íconos de todas
 * las apps en el Cajón de Aplicaciones (AppDrawer).
 */
data class MacIconRule(
    val resName: String,
    val packages: List<String>,
    val keywords: List<String>
)

val MacIconCatalog = listOf(
    MacIconRule("icon_maps", listOf("com.google.android.apps.maps"), listOf("google maps", "maps", "mapas")),
    MacIconRule("icon_waze", listOf("com.waze"), listOf("waze")),
    MacIconRule("icon_music", listOf("com.apple.android.music", "com.ts.music", "com.tw.music", "com.maxmpz.audioplayer"), listOf("apple music", "música", "musica", "music", "poweramp")),
    MacIconRule("icon_spotify", listOf("com.spotify.music"), listOf("spotify")),
    MacIconRule("icon_ytmusic", listOf("com.google.android.apps.youtube.music"), listOf("yt music", "youtube music")),
    MacIconRule("icon_youtube", listOf("com.google.android.youtube", "app.revanced.android.youtube"), listOf("youtube")),
    MacIconRule("icon_netflix", listOf("com.netflix.mediaclient"), listOf("netflix")),
    MacIconRule("icon_prime", listOf("com.amazon.avod.thirdpartyclient"), listOf("prime video")),
    MacIconRule("icon_disney", listOf("com.disney.disneyplus"), listOf("disney")),
    MacIconRule("icon_vlc", listOf("org.videolan.vlc", "com.ts.dvdplayer", "com.tw.video"), listOf("vlc", "video", "reproductor")),
    MacIconRule("icon_phone", listOf("com.ts.bt", "com.tw.bt", "com.syu.bt", "com.android.dialer", "com.google.android.dialer"), listOf("phone", "teléfono", "telefono", "bluetooth", "llamadas")),
    MacIconRule("icon_contacts", listOf("com.android.contacts", "com.google.android.contacts"), listOf("contactos", "contacts")),
    MacIconRule("icon_whatsapp", listOf("com.whatsapp"), listOf("whatsapp")),
    MacIconRule("icon_telegram", listOf("org.telegram.messenger"), listOf("telegram")),
    MacIconRule("icon_obd", listOf("org.prowl.torque", "org.prowl.torquefree", "com.ovz.carscanner", "com.ts.can"), listOf("torque", "obd", "car scanner", "canbus", "vehículo")),
    MacIconRule("icon_radio", listOf("com.ts.radio", "com.tw.radio", "com.syu.radio"), listOf("radio", "fm")),
    MacIconRule("icon_eq", listOf("com.ts.eq", "com.tw.eq", "com.syu.eq"), listOf("equalizer", "ecualizador", "dsp", "sonido")),
    MacIconRule("icon_camera", listOf("com.ts.dvr", "com.tw.dvr", "com.syu.dvr", "com.android.camera2", "com.ts.avin"), listOf("dvr", "cámara", "camara", "camera", "aux", "avin")),
    MacIconRule("icon_carplay", listOf("com.zjinnova.zlink", "cn.manstep.phonemirrorBox", "com.syu.carlink"), listOf("zlink", "tlink", "carplay", "autokit", "carlink")),
    MacIconRule("icon_chrome", listOf("com.android.chrome", "com.brave.browser", "org.mozilla.firefox"), listOf("chrome", "navegador", "browser", "safari")),
    MacIconRule("icon_files", listOf("com.android.documentsui", "com.ts.filemanager", "pl.solidexplorer2"), listOf("archivos", "files", "explorer", "gestor")),
    MacIconRule("icon_playstore", listOf("com.android.vending"), listOf("play store", "app store")),
    MacIconRule("icon_settings", listOf("com.android.settings", "com.ts.set", "com.tw.set"), listOf("ajustes", "settings", "configuración")),
    MacIconRule("icon_weather", listOf("com.google.android.googlequicksearchbox"), listOf("clima", "weather")),
    MacIconRule("icon_calculator", listOf("com.google.android.calculator", "com.android.calculator2"), listOf("calculadora", "calculator")),
    MacIconRule("icon_gallery", listOf("com.google.android.apps.photos", "com.android.gallery3d"), listOf("fotos", "photos", "galería", "galeria"))
)

/** Widget de accesos rápidos extendido (8 apps clave para el auto). */
val QuickShortcuts = listOf(
    Shortcut(
        "YouTube", Glyph.Screen,
        listOf("com.google.android.youtube", "com.ts.dvdplayer", "com.tw.video"),
        listOf("youtube", "video"),
        colors = Squircle.Video,
        iconResName = "icon_youtube"
    ),
    Shortcut(
        "Spotify", Glyph.Note,
        listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        listOf("spotify", "music"),
        colors = Squircle.Music,
        iconResName = "icon_spotify"
    ),
    Shortcut(
        "Radio FM", Glyph.Speaker,
        listOf("com.ts.radio", "com.tw.radio", "com.syu.radio"),
        listOf("radio", "fm"),
        colors = Squircle.Maps,
        iconResName = "icon_radio"
    ),
    Shortcut(
        "Cámara DVR", Glyph.Camera,
        listOf("com.ts.dvr", "com.tw.dvr", "com.syu.dvr", "com.android.camera2"),
        listOf("dvr", "cámara", "camara", "camera", "aux", "fcam"),
        colors = Squircle.Telemetry,
        iconResName = "icon_camera"
    ),
    Shortcut(
        "Waze", Glyph.Map,
        listOf("com.waze"),
        listOf("waze"),
        colors = Squircle.Maps,
        iconResName = "icon_waze"
    ),
    Shortcut(
        "Ecualizador", Glyph.Gauge,
        listOf("com.ts.eq", "com.tw.eq", "com.syu.eq"),
        listOf("ecualizador", "dsp", "eq"),
        colors = Squircle.Telemetry,
        iconResName = "icon_eq"
    ),
    Shortcut(
        "CarPlay/ZLink", Glyph.Phone,
        listOf("com.zjinnova.zlink", "cn.manstep.phonemirrorBox", "com.syu.carlink"),
        listOf("zlink", "tlink", "carplay", "autokit"),
        colors = Squircle.Phone,
        iconResName = "icon_carplay"
    ),
    Shortcut(
        "Ajustes", Glyph.Gear,
        listOf("com.android.settings", "com.ts.set"),
        listOf("ajustes", "settings", "config"),
        Settings.ACTION_SETTINGS,
        Squircle.Settings,
        iconResName = "icon_settings"
    )
)

val GoogleMapsShortcut = Shortcut("Google Maps", Glyph.Map, listOf("com.google.android.apps.maps"), listOf("google maps", "maps"))
val WazeShortcut = Shortcut("Waze", Glyph.Map, listOf("com.waze"), listOf("waze"))

object AppsRepository {
    @SuppressLint("DiscouragedApi")
    private fun loadResBitmap(context: Context, name: String): ImageBitmap? {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (resId == 0) return null
        return runCatching {
            ContextCompat.getDrawable(context, resId)?.toBitmap(192, 192)?.asImageBitmap()
        }.getOrNull()
    }

    private fun findRule(pkg: String, label: String): MacIconRule? {
        val lowerLabel = label.lowercase(Locale.ROOT)
        return MacIconCatalog.firstOrNull { pkg in it.packages }
            ?: MacIconCatalog.firstOrNull { r -> r.keywords.any { lowerLabel.contains(it) } }
    }

    @Suppress("DEPRECATION")
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val label = ri.loadLabel(pm).toString()
                val pkg = ri.activityInfo.packageName
                val rule = findRule(pkg, label)
                val baseCustom = rule?.let { loadResBitmap(context, it.resName) }
                val lightCustom = rule?.let { loadResBitmap(context, "${it.resName}_light") } ?: baseCustom
                val darkCustom = rule?.let { loadResBitmap(context, "${it.resName}_dark") } ?: baseCustom
                val fallback = baseCustom ?: darkCustom ?: lightCustom ?: ri.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
                AppEntry(
                    label = label,
                    packageName = pkg,
                    activityName = ri.activityInfo.name,
                    icon = fallback,
                    iconLight = lightCustom,
                    iconDark = darkCustom
                )
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun find(apps: List<AppEntry>, shortcut: Shortcut): AppEntry? =
        apps.firstOrNull { it.packageName in shortcut.packages }
            ?: apps.firstOrNull { app ->
                val l = app.label.lowercase(Locale.ROOT)
                shortcut.keywords.any { l.contains(it) }
            }
}

// =====================================================================
//  GPS: velocidad, rumbo, altitud
// =====================================================================

data class DriveState(
    val speedKmh: Float = 0f,
    val bearing: Float? = null,
    val altitude: Double? = null,
    val hasFix: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null
)

class LocationSource(private val context: Context) {
    private val _state = MutableStateFlow(DriveState())
    val state: StateFlow<DriveState> = _state
    private val manager = context.getSystemService(LocationManager::class.java)
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.value = DriveState(
                speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f,
                bearing = if (location.hasBearing()) location.bearing else _state.value.bearing,
                altitude = if (location.hasAltitude()) location.altitude else _state.value.altitude,
                hasFix = true,
                lat = location.latitude,
                lon = location.longitude
            )
        }

        @Deprecated("Requerido en APIs antiguas")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            _state.value = _state.value.copy(hasFix = false, speedKmh = 0f)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || manager == null) return
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 800L, 0f, listener, Looper.getMainLooper())
            running = true
        }
        runCatching {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { last ->
                _state.value = _state.value.copy(
                    lat = last.latitude, lon = last.longitude,
                    altitude = if (last.hasAltitude()) last.altitude else null
                )
            }
        }
    }

    fun stop() {
        if (!running) return
        runCatching { manager?.removeUpdates(listener) }
        running = false
    }
}

// =====================================================================
//  Música: MediaSession de cualquier app (con extractor de color vibrante)
// =====================================================================

class MediaListenerService : NotificationListenerService()

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val art: ImageBitmap? = null,
    val dominant: Color? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updateTime: Long = 0L,
    val speed: Float = 1f,
    val hasSession: Boolean = false,
    val hasAccess: Boolean = false,
    val appPackage: String? = null
) {
    fun livePosition(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) return positionMs
        val pos = positionMs + ((now - updateTime) * speed).toLong()
        return if (durationMs > 0) pos.coerceIn(0L, durationMs) else pos.coerceAtLeast(0L)
    }
}

/** Extrae el color más saturado y luminoso de una carátula (muestreo 8x8) para el Ambient Glow. */
private fun extractVibrantColor(bmp: Bitmap): Color {
    val small = Bitmap.createScaledBitmap(bmp, 8, 8, true)
    var bestColor = Color(0xFFFF375F)
    var bestScore = -1f
    val hsv = FloatArray(3)
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val pixel = small.getPixel(x, y)
            android.graphics.Color.colorToHSV(pixel, hsv)
            val sat = hsv[1]
            val v = hsv[2]
            // Priorizar colores vivos y no demasiado oscuros ni blancos puros
            val score = sat * 1.4f + (if (v in 0.35f..0.95f) 0.5f else 0f)
            if (score > bestScore) {
                bestScore = score
                // Asegurar brillo mínimo para el resplandor detrás del cristal
                hsv[1] = sat.coerceAtLeast(0.55f)
                hsv[2] = v.coerceIn(0.65f, 0.95f)
                bestColor = Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
    }
    return bestColor
}

class MediaSource(private val context: Context) {
    private val _state = MutableStateFlow(MediaInfo())
    val state: StateFlow<MediaInfo> = _state
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListenerService::class.java)
    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = attach(null)
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(pick(list))
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start() {
        if (!hasAccess() || sessions == null) {
            _state.value = MediaInfo(hasAccess = false)
            return
        }
        try {
            if (!listening) {
                sessions.addOnActiveSessionsChangedListener(sessionsListener, component)
                listening = true
            }
            attach(pick(sessions.getActiveSessions(component)))
        } catch (e: SecurityException) {
            _state.value = MediaInfo(hasAccess = false)
        }
    }

    fun stop() {
        if (listening) runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        listening = false
        attach(null)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    private fun pick(list: List<MediaController>?): MediaController? =
        list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()

    private fun attach(c: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, Handler(Looper.getMainLooper()))
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = MediaInfo(hasAccess = hasAccess())
            return
        }
        val m = c.metadata
        val ps = c.playbackState
        val raw = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val soft = raw?.let {
            runCatching { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }.getOrNull()
        }
        val dominant = soft?.let {
            runCatching { extractVibrantColor(it) }.getOrNull()
        }
        _state.value = MediaInfo(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            art = soft?.asImageBitmap(),
            dominant = dominant,
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING,
            positionMs = ps?.position ?: 0L,
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            updateTime = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed ?: 1f,
            hasSession = true,
            hasAccess = true,
            appPackage = c.packageName
        )
    }
}

// =====================================================================
//  Clima: Open-Meteo (Temperatura, código WMO y viento/humedad)
// =====================================================================

data class WeatherInfo(
    val tempC: Int? = null,
    val code: Int? = null,
    val description: String = "Esperando ubicación",
    val windKmh: Int? = null,
    val humidityPct: Int? = null
)

class WeatherSource {
    private val _state = MutableStateFlow(WeatherInfo())
    val state: StateFlow<WeatherInfo> = _state
    private var lastFetch = 0L

    suspend fun refresh(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastFetch < 15 * 60_000L) return
        lastFetch = now
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=" +
                        String.format(Locale.US, "%.3f", lat) + "&longitude=" +
                        String.format(Locale.US, "%.3f", lon) + "&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                try {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val current = JSONObject(body).getJSONObject("current")
                    val code = current.getInt("weather_code")
                    val wind = current.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }?.roundToInt()
                    val hum = current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 }
                    WeatherInfo(
                        tempC = current.getDouble("temperature_2m").roundToInt(),
                        code = code,
                        description = describeWeather(code),
                        windKmh = wind,
                        humidityPct = hum
                    )
                } finally {
                    conn.disconnect()
                }
            }
        }
        result.onSuccess { _state.value = it }
            .onFailure {
                lastFetch = 0L
                if (_state.value.tempC == null) _state.value = WeatherInfo(description = "Sin conexión")
            }
        }
}

fun describeWeather(code: Int): String = when (code) {
    0 -> "Despejado"
    1, 2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    in 51..67 -> "Lluvia"
    in 71..77 -> "Nieve"
    in 80..82 -> "Chubascos"
    in 95..99 -> "Tormenta"
    else -> "Variable"
}

// =====================================================================
//  Estado del dispositivo
// =====================================================================

data class DeviceStatus(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val gps: Boolean = false,
    val batteryPct: Int? = null,
    val charging: Boolean = false,
    val volume: Float = 0.5f
)

@SuppressLint("MissingPermission")
fun readDeviceStatus(context: Context): DeviceStatus {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val wifi = runCatching {
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)
    val bt = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)
    val gps = runCatching {
        context.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }.getOrDefault(false)

    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val present = battery?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val pct = if (present && level >= 0 && scale > 0) level * 100 / scale else null
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

    val am = context.getSystemService(AudioManager::class.java)
    val volume = if (am != null) {
        am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    } else 0.5f

    return DeviceStatus(wifi, bt, gps, pct, charging, volume)
}

// =====================================================================
//  Datos de demostración
// =====================================================================

object Demo {
    fun drive(t: Long) = DriveState(
        speedKmh = 64f + 22f * sin(t / 2600.0).toFloat(),
        bearing = ((t / 120L) % 360L).toFloat(),
        altitude = 32.0,
        hasFix = true
    )

    fun media(now: Long) = MediaInfo(
        title = "Canción de prueba",
        artist = "Artista de prueba",
        dominant = Color(0xFF7A3CFF),
        isPlaying = true,
        positionMs = 72_000L,
        durationMs = 214_000L,
        updateTime = now,
        hasSession = true,
        hasAccess = true
    )

    val weather = WeatherInfo(22, 2, "Parcialmente nublado", windKmh = 14, humidityPct = 58)
    val status = DeviceStatus(wifi = true, bluetooth = true, gps = true, batteryPct = 82, volume = 0.6f)
}
