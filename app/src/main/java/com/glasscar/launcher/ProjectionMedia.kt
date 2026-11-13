package com.glasscar.launcher

import android.app.Notification
import android.content.Context
import android.media.session.MediaSession
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

internal const val TLINK_PACKAGE = "com.zjinnova.zlink"

internal data class ProjectionTrack(val key: String, val token: MediaSession.Token?, val media: MediaInfo)

/** Only consume actual media notifications, never Tlink's connection/status notification. */
internal object ProjectionMedia {
    private val tracks = linkedMapOf<String, ProjectionTrack>()
    private val listeners = mutableSetOf<() -> Unit>()
    fun current() = tracks.values.toList()
    fun listen(listener: () -> Unit) { listeners += listener }
    fun unlisten(listener: () -> Unit) { listeners -= listener }
    private fun changed() { listeners.toList().forEach { it() } }
    fun clear() { tracks.clear(); changed() }
    fun remove(key: String) { if (tracks.remove(key) != null) changed() }
    fun post(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName != TLINK_PACKAGE) return
        val n = sbn.notification
        @Suppress("DEPRECATION")
        val token = runCatching { n.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION) }.getOrNull()
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val mediaStyle = n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle")
        if (!accepts(sbn.packageName, title, n.category, mediaStyle, token != null)) { remove(sbn.key); return }
        val art = runCatching { n.getLargeIcon()?.loadDrawable(context)?.toBitmap(256, 256)?.asImageBitmap() }.getOrNull()
        tracks[sbn.key] = ProjectionTrack(sbn.key, token, MediaInfo(title = title,
            artist = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(), art = art,
            hasSession = true, hasAccess = true, appPackage = TLINK_PACKAGE, canControl = false))
        changed()
    }
    internal fun accepts(pkg: String, title: String, category: String?, mediaStyle: Boolean, hasToken: Boolean) =
        pkg == TLINK_PACKAGE && (hasToken || (title.isNotBlank() &&
            title.lowercase() !in setOf("tlink", "tlink5", "zlink", "carplay") &&
            (category == Notification.CATEGORY_TRANSPORT || mediaStyle)))
}

/** Compare playback first; prefer projection among simultaneous playing sessions. */
internal fun mediaSessionPriority(pkg: String, playing: Boolean, hasTitle: Boolean, current: Boolean): Int =
    (if (playing) 100 else 0) + (if (pkg == TLINK_PACKAGE && (playing || hasTitle)) 20 else 0) +
        (if (hasTitle) 10 else 0) + (if (current) 1 else 0)

object MediaReport {
    var detail: String = "Sin sesiones observadas"
        internal set
    fun report() = "Música: $detail"
}
