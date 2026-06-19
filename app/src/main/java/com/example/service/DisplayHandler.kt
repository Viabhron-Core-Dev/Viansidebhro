package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object DisplayHandler {
    fun handleDisplayAction(context: Context, action: String) {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please grant Write Settings permission first", Toast.LENGTH_LONG).show()
            return
        }

        try {
            when (action) {
                "brightness_up" -> adjustBrightness(context, 25)
                "brightness_down" -> adjustBrightness(context, -25)
                "brightness_auto" -> toggleAutoBrightness(context)
                "timeout_cycle" -> cycleScreenTimeout(context)
                "orientation_toggle" -> toggleOrientationLock(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to apply setting", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustBrightness(context: Context, delta: Int) {
        val resolver = context.contentResolver
        try {
            // Ensure manual mode before adjusting
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            var newBrightness = current + delta
            if (newBrightness < 0) newBrightness = 0
            if (newBrightness > 255) newBrightness = 255
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
            Toast.makeText(context, "Brightness: ${(newBrightness * 100f / 255).toInt()}%", Toast.LENGTH_SHORT).show()
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
            Toast.makeText(context, "Brightness setting not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAutoBrightness(context: Context) {
        val resolver = context.contentResolver
        try {
            val currentMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            val newMode = if (currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, newMode)
            val modeStr = if (newMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "Auto" else "Manual"
            Toast.makeText(context, "Brightness: $modeStr", Toast.LENGTH_SHORT).show()
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun cycleScreenTimeout(context: Context) {
        val resolver = context.contentResolver
        val timeouts = intArrayOf(15000, 30000, 60000, 120000, 300000, 600000) // 15s, 30s, 1m, 2m, 5m, 10m
        try {
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT)
            var nextIndex = timeouts.indexOfFirst { it > current }
            if (nextIndex == -1) nextIndex = 0
            val nextTimeout = timeouts[nextIndex]
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, nextTimeout)
            Toast.makeText(context, "Screen timeout: ${nextTimeout / 1000}s", Toast.LENGTH_SHORT).show()
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun toggleOrientationLock(context: Context) {
        val resolver = context.contentResolver
        try {
            val current = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION)
            val next = if (current == 1) 0 else 1
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, next)
            val stateStr = if (next == 1) "Auto Rotate" else "Portrait Locked"
            Toast.makeText(context, "Rotation: $stateStr", Toast.LENGTH_SHORT).show()
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
    }
}
