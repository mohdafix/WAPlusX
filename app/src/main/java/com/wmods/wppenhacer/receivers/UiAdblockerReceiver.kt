package com.wmods.wppenhacer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.wmods.wppenhacer.BuildConfig

class UiAdblockerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.wmods.wppenhacer.UI_ELEMENT_CAPTURED") return

        val value = intent.getStringExtra("captured_value") ?: return
        val isSelector = intent.getBooleanExtra("is_selector", false)

        val prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID + "_preferences", Context.MODE_PRIVATE)
        val key = if (isSelector) "hidden_ui_element_selectors" else "hidden_ui_element_ids"

        val current = prefs.getString(key, "") ?: ""
        val values = current.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (!values.contains(value)) {
            values.add(value)
            val newString = values.joinToString("\n")
            prefs.edit().putString(key, newString).apply()
            
            // Try to make file world-readable for Xposed
            try {
                val prefFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/" + BuildConfig.APPLICATION_ID + "_preferences.xml")
                prefFile.setReadable(true, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            Toast.makeText(context, "Added to UI Adblocker filter!", Toast.LENGTH_SHORT).show()
        }
    }
}
