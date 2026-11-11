package com.glasscar.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import kotlin.math.roundToInt

/** Android and MCU volume are different on many stereos. Never pretend one is the other. */
object HeadUnitControls {
    fun isHeadUnit(context: Context): Boolean {
        val hardware = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.DEVICE}".lowercase()
        if (listOf("topway", "ts10", "ts18", "fyt", "uis7862", "microntek", "xyauto").any(hardware::contains)) return true
        val nativePackages = listOf("com.ts.music", "com.tw.music", "com.syu.music", "com.ts.MainUI", "com.tw.canbus")
        return nativePackages.any { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
    }

    fun brightnessValue(fraction: Float): Int = (fraction.coerceIn(0.12f, 1f) * 255).roundToInt()
    fun readBrightness(context: Context): Float = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.65f).coerceIn(0.12f, 1f)

    fun setBrightness(context: Context, window: Window, fraction: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val value = brightnessValue(fraction)
        return runCatching {
            val resolver = context.contentResolver
            val manual = Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val written = Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            if (manual && written) {
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            manual && written
        }.getOrDefault(false)
    }

    fun setAndroidVolume(context: Context, fraction: Float) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).roundToInt(), 0)
    }

    /** Open the stereo's own controls when its MCU is not exposed through Android AudioManager. */
    fun openNativePanel(context: Context): Boolean {
        val expanded = runCatching {
            val service = context.getSystemService("statusbar") ?: return@runCatching false
            service.javaClass.getMethod("expandSettingsPanel", String::class.java).invoke(service, null)
            true
        }.getOrDefault(false)
        if (expanded) return true
        return runCatching {
            val action = if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
