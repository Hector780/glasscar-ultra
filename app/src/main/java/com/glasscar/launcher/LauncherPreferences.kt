package com.glasscar.launcher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LauncherConfig(
    val assignments: Map<String, String> = emptyMap(),
    val recent: List<String> = emptyList(),
    val widgets: Map<String, Int> = emptyMap(),
    val night: Boolean = true,
    val cameraId: String? = null,
    val dashboardCamera: Boolean = false,
    val nativeAudio: Boolean = false
)

val AppEntry.componentKey: String get() = "$packageName/$activityName"

/** All overrides use component names, never translated labels or list positions. */
class LauncherPreferences(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("glasscar_v4", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<LauncherConfig> = _state

    private fun read() = LauncherConfig(
        assignments = prefs.all.filterKeys { it.startsWith("app.") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("app.") to it } }.toMap(),
        recent = prefs.getString("recent", "").orEmpty().split('|').filter(String::isNotBlank),
        widgets = prefs.all.filterKeys { it.startsWith("widget.") }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix("widget.") to it } }.toMap(),
        night = prefs.getBoolean("night", true),
        cameraId = prefs.getString("camera", null),
        dashboardCamera = prefs.getBoolean("dashboardCamera", false),
        nativeAudio = prefs.getBoolean("nativeAudio", HeadUnitControls.isHeadUnit(context))
    )

    private fun changed() { _state.value = read() }
    fun assign(slot: String, app: AppEntry?) {
        prefs.edit().apply { if (app == null) remove("app.$slot") else putString("app.$slot", app.componentKey) }.apply()
        changed()
    }
    fun record(app: AppEntry) {
        val recent = (listOf(app.componentKey) + _state.value.recent).distinct().take(12)
        prefs.edit().putString("recent", recent.joinToString("|")).apply()
        changed()
    }
    fun recordRecent(keys: List<String>) {
        val recent = (keys + _state.value.recent).distinct().take(12)
        prefs.edit().putString("recent", recent.joinToString("|")).apply(); changed()
    }
    fun setNight(value: Boolean) { prefs.edit().putBoolean("night", value).apply(); changed() }
    fun setNativeAudio(value: Boolean) { prefs.edit().putBoolean("nativeAudio", value).apply(); changed() }
    fun setCamera(id: String?) { prefs.edit().putString("camera", id).apply(); changed() }
    fun setDashboardCamera(value: Boolean) { prefs.edit().putBoolean("dashboardCamera", value).apply(); changed() }
    fun setWidget(slot: String, id: Int?) {
        prefs.edit().apply { if (id == null) remove("widget.$slot") else putInt("widget.$slot", id) }.apply()
        changed()
    }
}

fun resolveApp(apps: List<AppEntry>, config: LauncherConfig, slot: String, shortcut: Shortcut): AppEntry? {
    val override = config.assignments[slot]
    // A missing user-selected app must not silently open some unrelated app instead.
    return if (override != null) apps.firstOrNull { it.componentKey == override }
    else AppsRepository.find(apps, shortcut)
}

fun recentApps(apps: List<AppEntry>, config: LauncherConfig): List<AppEntry> =
    config.recent.mapNotNull { key -> apps.firstOrNull { it.componentKey == key } }
        .distinctBy { it.packageName }.take(4)
