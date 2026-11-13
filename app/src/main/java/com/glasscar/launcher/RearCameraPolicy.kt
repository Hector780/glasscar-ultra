package com.glasscar.launcher

import android.content.ComponentName
import android.content.Intent

/** OEM reverse owns global camera/MCU state. Keep it on the factory display, outside embedding. */
internal object RearCameraPolicy {
    fun intent() = Intent(Intent.ACTION_MAIN)
        .setComponent(ComponentName("com.tw.reverse", "com.tw.reverse.ReverseActivity"))
        // Same manual-preview extra used by TWService; it clears itself on real reverse engagement.
        .putExtra("iskeypad", 1)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
