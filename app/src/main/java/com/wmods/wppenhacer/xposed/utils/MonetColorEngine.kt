package com.wmods.wppenhacer.xposed.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

object MonetColorEngine {

    @ColorInt
    @JvmStatic
    fun getSystemAccentColor(context: Context): Int {
        Utils.xprefs?.reload()

        val customEnabled = Utils.xprefs?.getBoolean("monet_custom_color_enabled", false) ?: false
        if (customEnabled) {
            val customColor = Utils.xprefs?.getInt("monet_custom_color", 0xFF25D366.toInt()) ?: 0xFF25D366.toInt()
            val shade = if (isNightMode(context)) 200 else 600
            return generateShade(customColor, shade)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val palette = Utils.xprefs?.getString("monet_palette", "accent1") ?: "accent1"
                val colorName = "system_${palette}${if (isNightMode(context)) "_200" else "_600"}"
                val resId = context.resources.getIdentifier(colorName, "color", "android")
                if (resId != 0) {
                    return context.getColor(resId)
                }

                // Fallback to legacy logic if dynamic lookup fails
                val fallbackResId = if (isNightMode(context))
                    android.R.color.system_accent1_200
                else
                    android.R.color.system_accent1_600
                return context.getColor(fallbackResId)
            } catch (e: Exception) {
                return getColorFromAttr(context, android.R.attr.colorAccent)
            }
        }
        return -1
    }

    @ColorInt
    @JvmStatic
    fun getSystemPrimaryColor(context: Context): Int {
        return getSystemAccentColor(context)
    }

    @ColorInt
    @JvmStatic
    fun getSystemSecondaryColor(context: Context): Int {
        Utils.xprefs?.reload()
        if (Utils.xprefs?.getBoolean("monet_custom_color_enabled", false) == true) {
            val customColor = Utils.xprefs?.getInt("monet_custom_color", 0xFF25D366.toInt()) ?: 0xFF25D366.toInt()
            val hsv = FloatArray(3)
            Color.colorToHSV(customColor, hsv)
            hsv[1] *= 0.5f // Desaturate
            val secondaryBase = Color.HSVToColor(hsv)
            return generateShade(secondaryBase, if (isNightMode(context)) 200 else 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val palette = Utils.xprefs?.getString("monet_palette", "accent1") ?: "accent1"
                val secondaryPalette = if (palette == "accent1") "accent2" else "accent1"

                val colorName = "system_${secondaryPalette}${if (isNightMode(context)) "_200" else "_500"}"
                val resId = context.resources.getIdentifier(colorName, "color", "android")
                if (resId != 0) return context.getColor(resId)

                val fallbackResId = if (isNightMode(context))
                    android.R.color.system_accent2_200
                else
                    android.R.color.system_accent2_500
                return context.getColor(fallbackResId)
            } catch (e: Exception) {
                return getColorFromAttr(context, android.R.attr.colorSecondary)
            }
        }
        return -1
    }

    @ColorInt
    @JvmStatic
    fun getSystemBackgroundColor(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                if (isNightMode(context)) {
                    Color.BLACK
                } else {
                    context.getColor(android.R.color.system_neutral1_50)
                }
            } catch (e: Exception) {
                -1
            }
        }
        return -1
    }

    @ColorInt
    @JvmStatic
    fun getBubbleOutgoingColor(context: Context): Int {
        Utils.xprefs?.reload()
        if (Utils.xprefs?.getBoolean("monet_custom_color_enabled", false) == true) {
            val customColor = Utils.xprefs?.getInt("monet_custom_color", 0xFF25D366.toInt()) ?: 0xFF25D366.toInt()
            return generateShade(customColor, 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val palette = Utils.xprefs?.getString("monet_palette", "accent1") ?: "accent1"
                val colorName = "system_${palette}_500"
                val resId = context.resources.getIdentifier(colorName, "color", "android")
                if (resId != 0) return context.getColor(resId)

                return context.getColor(android.R.color.system_accent1_500)
            } catch (e: Exception) {
                return -1
            }
        }
        return -1
    }

    @ColorInt
    @JvmStatic
    fun getBubbleIncomingColor(context: Context): Int {
        Utils.xprefs?.reload()
        if (Utils.xprefs?.getBoolean("monet_custom_color_enabled", false) == true) {
            val customColor = Utils.xprefs?.getInt("monet_custom_color", 0xFF25D366.toInt()) ?: 0xFF25D366.toInt()
            val hsv = FloatArray(3)
            Color.colorToHSV(customColor, hsv)
            hsv[0] = (hsv[0] + 45) % 360
            hsv[1] = Math.max(hsv[1], 0.8f)
            val incomingBase = Color.HSVToColor(hsv)
            return generateShade(incomingBase, if (isNightMode(context)) 200 else 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val palette = Utils.xprefs?.getString("monet_palette", "accent1") ?: "accent1"
                val incomingPalette = if (palette == "accent1") "accent3" else "neutral2"

                val colorName = "system_${incomingPalette}${if (isNightMode(context)) "_200" else "_500"}"
                val resId = context.resources.getIdentifier(colorName, "color", "android")
                if (resId != 0) return context.getColor(resId)

                val fallbackResId = if (isNightMode(context))
                    android.R.color.system_accent3_200
                else
                    android.R.color.system_accent3_500
                return context.getColor(fallbackResId)
            } catch (e: Exception) {
                return -1
            }
        }
        return -1
    }

    @ColorInt
    private fun generateShade(baseColor: Int, shade: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)

        hsl[1] = Math.max(hsl[1], 0.85f)

        when (shade) {
            200 -> hsl[2] = 0.50f
            500 -> hsl[2] = 0.45f
            600 -> {
                hsl[2] = 0.45f
                hsl[1] = 1.0f
            }
        }
        return ColorUtils.HSLToColor(hsl)
    }

    private fun isNightMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    @ColorInt
    private fun getColorFromAttr(context: Context, @androidx.annotation.AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        try {
            if (context.theme.resolveAttribute(attr, typedValue, true)) {
                if (typedValue.resourceId != 0) {
                    return context.getColor(typedValue.resourceId)
                }
                return typedValue.data
            }
        } catch (e: Exception) {
            Log.e("MonetEngine", "Failed to resolve attr: ${e.message}")
        }
        return -1
    }
}
