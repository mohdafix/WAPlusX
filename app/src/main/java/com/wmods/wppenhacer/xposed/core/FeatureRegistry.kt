package com.wmods.wppenhacer.xposed.core

import android.content.SharedPreferences
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.ConcurrentHashMap

/**
 * FeatureRegistry - Manages lazy loading of features
 */
object FeatureRegistry {

    private const val TAG = "FeatureRegistry"

    // Registry of lazy features - maps feature class to its registration
    private val lazyFeatures = ConcurrentHashMap<String, LazyFeatureRegistration>()

    // Track which features have been loaded
    private val loadedFeatures = ConcurrentHashMap.newKeySet<String>()

    // Lock for thread-safe feature loading
    private val loadLock = Any()

    /**
     * Represents a lazy feature that can be loaded on-demand
     */
    class LazyFeatureRegistration(
        val featureName: String,
        val featureClass: Class<out Feature>,
        val triggerType: TriggerType,
        val triggerAction: String? = null, // activity name, method name, etc.
        val showInSettings: Boolean = true
    )

    /**
     * Types of triggers that can activate lazy features
     */
    enum class TriggerType {
        ACTIVITY_RESUMED,      // When specific activity becomes visible
        ACTIVITY_CREATED,      // When specific activity is created
        METHOD_CALLED,         // When specific method is invoked
        MANUAL,                // Manual activation via settings
        STATUS_VIEW,           // When user views status
        MESSAGE_DELETED,       // When a message is deleted
        CALL_STARTED,          // When a call starts
        CONVERSATION_OPENED   // When conversation is opened
    }

    /**
     * Register a feature as lazy - it will only load when triggered
     */
    fun registerLazyFeature(
        featureName: String,
        featureClass: Class<out Feature>,
        triggerType: TriggerType,
        triggerAction: String? = null,
        showInSettings: Boolean = true
    ) {
        lazyFeatures[featureClass.simpleName] =
            LazyFeatureRegistration(featureName, featureClass, triggerType, triggerAction, showInSettings)
        XposedBridge.log("[FeatureRegistry] Registered lazy feature: $featureName (trigger: $triggerType)")
    }

    /**
     * Check if a feature is registered as lazy
     */
    fun isLazyFeature(featureName: String): Boolean {
        return lazyFeatures.containsKey(featureName)
    }

    /**
     * Check if a feature has already been loaded
     */
    fun isLoaded(featureName: String): Boolean {
        return loadedFeatures.contains(featureName)
    }

    /**
     * Activate a lazy feature based on trigger
     */
    fun activateFeature(
        triggerType: TriggerType,
        triggerAction: String? = null,
        loader: ClassLoader,
        pref: SharedPreferences
    ): Boolean {
        var loaded = false
        // Find all lazy features that match this trigger
        for (entry in lazyFeatures.entries) {
            val reg = entry.value

            // Skip if already loaded
            if (loadedFeatures.contains(entry.key)) {
                continue
            }

            // Check if this trigger matches
            if (reg.triggerType == triggerType &&
                (triggerAction == null || triggerAction == reg.triggerAction || reg.triggerAction == null)
            ) {
                loadFeature(reg, loader, pref)
                loaded = true
            }
        }
        return loaded
    }

    /**
     * Manually activate a lazy feature by name
     */
    fun activateFeatureByName(featureName: String, loader: ClassLoader, pref: SharedPreferences): Boolean {
        val reg = lazyFeatures[featureName]
        if (reg != null && !loadedFeatures.contains(featureName)) {
            loadFeature(reg, loader, pref)
            return true
        }
        return false
    }

    /**
     * Load a lazy feature
     */
    private fun loadFeature(reg: LazyFeatureRegistration, loader: ClassLoader, pref: SharedPreferences) {
        synchronized(loadLock) {
            val key = reg.featureClass.simpleName
            if (loadedFeatures.contains(key)) {
                return
            }

            val startTime = System.currentTimeMillis()
            try {
                val constructor = reg.featureClass.getConstructor(
                    ClassLoader::class.java, SharedPreferences::class.java
                )
                val feature = constructor.newInstance(loader, pref) as Feature
                feature.doHook()

                loadedFeatures.add(key)
                val loadTime = System.currentTimeMillis() - startTime
                XposedBridge.log("[FeatureRegistry] Lazy loaded: ${reg.featureName} in ${loadTime}ms")

            } catch (e: Throwable) {
                XposedBridge.log("[FeatureRegistry] Failed to lazy load ${reg.featureName}: ${e.message}")
            }
        }
    }

    /**
     * Get list of all lazy features for settings display
     */
    fun getLazyFeatures(): Map<String, LazyFeatureRegistration> {
        return lazyFeatures.toMap()
    }

    /**
     * Get count of loaded features
     */
    fun getLoadedCount(): Int = loadedFeatures.size

    /**
     * Get count of registered lazy features
     */
    fun getRegisteredCount(): Int = lazyFeatures.size

    /**
     * Clear loaded status (useful for testing or reset)
     */
    fun reset() {
        loadedFeatures.clear()
    }
}
