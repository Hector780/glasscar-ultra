package com.glasscar.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Uses Android's optional Usage Access grant. Without it, launcher-opened recents still work. */
@Suppress("DEPRECATION")
fun readSystemRecents(context: Context, apps: List<AppEntry>): List<String> = runCatching {
    val ops = context.getSystemService(AppOpsManager::class.java)
    if (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) != AppOpsManager.MODE_ALLOWED)
        return emptyList()
    val now = System.currentTimeMillis()
    context.getSystemService(UsageStatsManager::class.java)
        .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 60 * 60_000L, now)
        .filter { it.lastTimeUsed > now - 24 * 60 * 60_000L && it.packageName != context.packageName }
        .sortedByDescending { it.lastTimeUsed }
        .distinctBy { it.packageName }
        .mapNotNull { usage -> apps.firstOrNull { it.packageName == usage.packageName }?.componentKey }
        .take(12)
}.getOrDefault(emptyList())
