package com.glasscar.launcher

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Uses only actual Tlink call notifications; never interprets connection or music text as a call. */
internal object ProjectionCalls {
    private val keys = mutableSetOf<String>()
    private var vendorCall = false
    private var lastVendorStatus = "sin señal"
    private val state = MutableStateFlow(false)
    val active: StateFlow<Boolean> = state
    fun post(sbn: StatusBarNotification) = update(sbn.key, sbn.packageName, sbn.notification.category)
    internal fun update(key: String, pkg: String, category: String?) {
        if (pkg == TLINK_PACKAGE && category == Notification.CATEGORY_CALL) keys.add(key) else keys.remove(key)
        publish()
    }
    fun remove(key: String) { keys.remove(key); publish() }
    fun clear() { keys.clear(); publish() }
    private fun publish() { state.value = vendorCall || keys.isNotEmpty() }

    // Contract observed in the original TWService's receiver com.tw.service.u, onReceive.
    // This changes only GlassCar's selected tab. It never forwards commands to TW/MCU.
    fun vendorStatus(action: String?, status: String?) {
        if (action != TLINK_PACKAGE) return
        when (status) {
            "PHONE_CALL_ON" -> vendorCall = true
            "PHONE_CALL_OFF", "DISCONNECT" -> vendorCall = false
            else -> return
        }
        lastVendorStatus = status.orEmpty()
        publish()
    }
    fun clearVendor() { vendorCall = false; publish() }
    fun report() = "CarPlay llamada: ${state.value} · señal TW: $lastVendorStatus · notificaciones de llamada: ${keys.size}"
}

internal fun routesToCarPlay(packageName: String, embedding: Boolean) = embedding && packageName == TLINK_PACKAGE
