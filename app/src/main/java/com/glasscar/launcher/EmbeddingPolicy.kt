package com.glasscar.launcher

/** A deliberately narrow broker, not a shell-command or general input API. */
object EmbeddingPolicy {
    val components = mapOf(
        "com.google.android.apps.maps" to "com.google.android.apps.maps/com.google.android.maps.MapsActivity",
        "com.zjinnova.zlink" to "com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity",
        // Public launcher from HD Car DVR 2.13.3-gsj (target 29); its internal MainActivity alias is not exported.
        "com.williexing.android.apps.xcdvr1" to "com.williexing.android.apps.xcdvr1/com.williexing.android.apps.xcdvr2.Splash"
    )
    fun displayName(uid: Int, target: String) = "GlassCarEmbed:$uid:$target"
    fun allowsDisplay(id: Int, ownerUid: Int, callerUid: Int, name: String, target: String): Boolean =
        id > 0 && callerUid >= 10000 && ownerUid == callerUid && target in components &&
            name == displayName(callerUid, target)

    fun launchCommand(id: Int, target: String): List<String> {
        require(id > 0) { "No se permite iniciar en la pantalla principal" }
        val component = requireNotNull(components[target]) { "Aplicación fuera de alcance" }
        // Separate arguments: no shell interpolation and no caller-controlled command text.
        return listOf("/system/bin/am", "start", "--user", "current", "--display", id.toString(),
            "-n", component, "-f", "0x10000000")
    }
}
