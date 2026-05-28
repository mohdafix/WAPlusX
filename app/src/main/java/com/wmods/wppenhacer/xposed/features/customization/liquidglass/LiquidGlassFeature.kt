package com.wmods.wppenhacer.xposed.features.customization.liquidglass

import android.content.Context
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import de.robv.android.xposed.XSharedPreferences

class LiquidGlassFeature(
    loader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(loader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("liquid_glass_enabled", false)) return
        val context = FeatureLoader.mApp as? Context ?: return
        WhatsAppLiquidGlassHooks(context, classLoader, prefs).init()
    }

    override fun getPluginName(): String {
        return "Liquid Glass Navigation Bar"
    }
}
