package com.glasscar.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** System picker grants widget access; no hidden cross-app Activity embedding or root. */
class WidgetController(private val activity: ComponentActivity, private val preferences: LauncherPreferences) {
    val host = AppWidgetHost(activity, 4071)
    private var pendingSlot: String? = null
    private var pendingId = -1
    private var configuring = false
    private val result: androidx.activity.result.ActivityResultLauncher<Intent> = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        if (response.resultCode != Activity.RESULT_OK) cancel()
        else {
            val id = response.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
            val info = AppWidgetManager.getInstance(activity).getAppWidgetInfo(id)
            if (id < 0 || info == null) cancel()
            else if (info.configure != null && !configuring) {
                pendingId = id
                configuring = true
                runCatching {
                    launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                }.onFailure { cancel() }
            } else {
                pendingSlot?.let { slot ->
                    preferences.state.value.widgets[slot]?.takeIf { it != id }?.let(host::deleteAppWidgetId)
                    preferences.setWidget(slot, id)
                }
                pendingSlot = null; pendingId = -1; configuring = false
            }
        }
    }

    private fun launch(intent: Intent) = result.launch(intent)
    fun choose(slot: String) {
        cancel()
        pendingSlot = slot
        pendingId = host.allocateAppWidgetId()
        runCatching {
            launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId))
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
