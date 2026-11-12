package com.glasscar.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Only offer providers belonging to the selected app, never unrelated system widgets. */
class WidgetController(private val activity: ComponentActivity, private val preferences: LauncherPreferences) {
    val host = AppWidgetHost(activity, 4071)
    private var pendingSlot: String? = null
    private var pendingId = -1
    private var configuring = false
    private val result = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        if (response.resultCode != Activity.RESULT_OK) cancel()
        else {
            val id = response.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
            // Do not accept an unrelated widget ID returned by an external activity.
            if (id != pendingId) cancel() else finishBinding()
        }
    }

    private fun finishBinding() {
        val info = AppWidgetManager.getInstance(activity).getAppWidgetInfo(pendingId)
        if (pendingId < 0 || info == null) { cancel(); return }
        if (info.configure != null && !configuring) {
            configuring = true
            runCatching {
                result.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId))
            }.onFailure { cancel() }
        } else {
            pendingSlot?.let { slot ->
                preferences.state.value.widgets[slot]?.takeIf { it != pendingId }?.let(host::deleteAppWidgetId)
                preferences.setWidget(slot, pendingId)
            }
            pendingSlot = null; pendingId = -1; configuring = false
        }
    }

    fun choose(slot: String) {
        cancel()
        val apps = AppsRepository.load(activity)
        val (appSlot, shortcut) = when (slot) {
            "map" -> "maps" to DockShortcuts[0]
            "camera" -> "camera" to QuickShortcuts[3]
            "carplay" -> "carplay" to QuickShortcuts[6]
            else -> return
        }
        val providers = matchingWidgets(activity, resolveApp(apps, preferences.state.value, appSlot, shortcut))
        if (providers.isEmpty()) {
            Toast.makeText(activity, "La aplicación seleccionada no ofrece widgets", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(activity).setTitle("Widget de la aplicación")
            .setItems(providers.map { it.loadLabel(activity.packageManager) }.toTypedArray()) { _, index -> bind(slot, providers[index]) }
            .setNegativeButton("Cancelar", null).show()
    }
    private fun bind(slot: String, provider: AppWidgetProviderInfo) {
        pendingSlot = slot; pendingId = host.allocateAppWidgetId()
        runCatching {
            val manager = AppWidgetManager.getInstance(activity)
            if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.profile, provider.provider, null)) finishBinding()
            else result.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile))
        }.onFailure { cancel() }
    }
    fun remove(slot: String) {
        preferences.state.value.widgets[slot]?.let(host::deleteAppWidgetId)
        preferences.setWidget(slot, null)
    }
    private fun cancel() {
        if (pendingId >= 0) host.deleteAppWidgetId(pendingId)
        pendingSlot = null; pendingId = -1; configuring = false
    }
    fun restore(state: Bundle?) {
        pendingSlot = state?.getString("widgetSlot")
        pendingId = state?.getInt("widgetId", -1) ?: -1
        configuring = state?.getBoolean("widgetConfiguring", false) ?: false
    }
    fun save(state: Bundle) {
        state.putString("widgetSlot", pendingSlot); state.putInt("widgetId", pendingId)
        state.putBoolean("widgetConfiguring", configuring)
    }
}
