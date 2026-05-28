package com.wmods.wppenhacer.xposed.features.customization.uiadblocker

import android.content.Context
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import de.robv.android.xposed.XSharedPreferences

class UiAdblockerFeature(
    loader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(loader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("hide_ui_elements", false) && !prefs.getBoolean("capture_ui_elements", false)) return
        val context = FeatureLoader.mApp as? Context ?: return
        WhatsAppUiElementHooks(prefs, context, classLoader).init()
    }

    override fun getPluginName(): String {
        return "UI Adblocker"
    }
}
