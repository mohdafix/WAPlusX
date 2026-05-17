package com.wmods.wppenhacer.xposed.utils

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.xposed.core.WppCore
import java.util.HashMap

object DesignUtils {

    @JvmField
    val AURA_COLORS: Map<String, Int> = object : HashMap<String, Int>() {
        init {
            put("green", -0xda2c9a)
            put("blue", -0xcb480f)
            put("purple", -0x63d850)
            put("pink", -0x16e19d)
            put("red", -0xbbe1ca)
            put("orange", -0x67ff)
            put("yellow", -0x14c5)
            put("lime", -0x3223c7)
            put("teal", -0xff6978)
            put("cyan", -0xff432c)
            put("indigo", -0xc0ae4b)
            put("deep_purple", -0x98c549)
            put("brown", -0x86aa78)
            put("grey", -0x616162)
            put("black", -0x1000000)
        }
    }

    private var mPrefs: SharedPreferences? = null

    @SuppressLint("UseCompatLoadingForDrawables")
    @JvmStatic
    fun getDrawable(id: Int): Drawable? {
        if (id == 0) return null
        return try {
            Utils.getApplication().getDrawable(id)
        } catch (e: Exception) {
            null
        }
    }

    @Nullable
    @JvmStatic
    fun getDrawableByName(name: String): Drawable? {
        val id = Utils.getID(name, "drawable")
        if (id == 0) return null
        return getDrawable(id)
    }

    @Nullable
    @JvmStatic
    fun getIconByName(name: String, isTheme: Boolean): Drawable? {
        val id = Utils.getID(name, "drawable")
        if (id == 0) return null
        val icon = getDrawable(id)
        if (isTheme && icon != null) {
            return coloredDrawable(icon, if (isNightMode()) Color.WHITE else Color.BLACK)
        }
        return icon
    }

    @NonNull
    @JvmStatic
    fun coloredDrawable(drawable: Drawable, color: Int): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
        } else {
            @Suppress("DEPRECATION")
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        return drawable
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @JvmStatic
    fun alphaDrawable(drawable: Drawable, primaryTextColor: Int, alpha: Int): Drawable {
        val colored = coloredDrawable(drawable, primaryTextColor)
        colored.alpha = alpha
        return colored
    }

    @NonNull
    @JvmStatic
    fun createDrawable(type: String, color: Int): Drawable {
        return when (type) {
            "rc_dialog_bg" -> {
                val border = Utils.dipToPixels(12.0f).toFloat()
                val shapeDrawable = ShapeDrawable(
                    RoundRectShape(floatArrayOf(border, border, border, border, 0f, 0f, 0f, 0f), null, null)
                )
                shapeDrawable.paint.color = color
                shapeDrawable
            }
            "selector_bg" -> {
                val border = Utils.dipToPixels(18.0f).toFloat()
                val selectorBg = ShapeDrawable(
                    RoundRectShape(floatArrayOf(border, border, border, border, border, border, border, border), null, null)
                )
                selectorBg.paint.color = color
                selectorBg
            }
            "rc_dotline_dialog" -> {
                val border = Utils.dipToPixels(16.0f).toFloat()
                val shapeDrawable = ShapeDrawable(
                    RoundRectShape(floatArrayOf(border, border, border, border, border, border, border, border), null, null)
                )
                shapeDrawable.paint.color = color
                shapeDrawable
            }
            "oval" -> {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }
            "rect" -> {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = Utils.dipToPixels(8.0f).toFloat()
                    setColor(color)
                }
            }
            "stroke_border" -> {
                val radius = Utils.dipToPixels(18.0f).toFloat()
                val outerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
                val roundRectShape = RoundRectShape(outerRadii, null, null)
                val shapeDrawable = ShapeDrawable(roundRectShape).apply {
                    paint.color = Color.TRANSPARENT
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = Utils.dipToPixels(2.0f).toFloat()
                    paint.color = color
                }
                val inset = Utils.dipToPixels(2.0f)
                InsetDrawable(shapeDrawable, inset, inset, inset, inset)
            }
            else -> ColorDrawable(Color.BLACK)
        }
    }

    @JvmStatic
    fun getPrimaryTextColor(): Int {
        val prefs = mPrefs ?: return if (isNightMode()) -0x2 else -0xfffffe
        val textColor = prefs.getInt("text_color", 0)
        if (textColor == 0 || !prefs.getBoolean("changecolor", false)) {
            return if (isNightMode()) -0x2 else -0xfffffe
        }
        return textColor
    }

    @JvmStatic
    fun getUnSeenColor(): Int {
        val prefs = mPrefs ?: return -0xda2c9a
        
        // Priority 1: Manual Color
        if (prefs.getBoolean("changecolor", false)) {
            val manualColor = prefs.getInt("primary_color", 0)
            if (manualColor != 0) return manualColor
        }

        // Priority 2: Monet
        if (prefs.getBoolean("monet_theme", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val monetColor = MonetColorEngine.getSystemAccentColor(Utils.getApplication())
            if (monetColor != -1) return monetColor
        }

        return -0xda2c9a // 0xFF25d366
    }

    @JvmStatic
    fun getPrimarySurfaceColor(): Int {
        val prefs = mPrefs ?: return if (isNightMode()) -0xededed else -0x2
        val backgroundColor = prefs.getInt("background_color", 0)
        if (backgroundColor == 0 || !prefs.getBoolean("changecolor", false)) {
            return if (isNightMode()) -0xededed else -0x2
        }
        return backgroundColor
    }

    @JvmStatic
    fun generatePrimaryColorDrawable(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        val prefs = mPrefs ?: return null
        val primaryColorInt = prefs.getInt("primary_color", 0)
        if (primaryColorInt != 0 && prefs.getBoolean("changecolor", false)) {
            val bitmap = drawableToBitmap(drawable)
            val color = getDominantColor(bitmap)
            val replaced = replaceColor(bitmap, color, primaryColorInt, 120.0)
            return BitmapDrawable(Utils.getApplication().resources, replaced)
        }
        return null
    }

    @JvmStatic
    fun isNightMode(): Boolean {
        return if (WppCore.getDefaultTheme() <= 0) isNightModeBySystem() else WppCore.getDefaultTheme() == 2
    }

    @JvmStatic
    fun isNightModeBySystem(): Boolean {
        return (Utils.getApplication().resources.configuration.uiMode and 48) == 32
    }

    @JvmStatic
    fun setPrefs(prefs: SharedPreferences?) {
        mPrefs = prefs
    }

    @JvmStatic
    fun isLight(color: Int): Boolean {
        if (Color.alpha(color) < 128) {
            return !isNightMode()
        }
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return luminance > 0.5
    }

    @JvmStatic
    fun getContrastColor(color: Int): Int {
        return if (isLight(color)) Color.BLACK else Color.WHITE
    }

    @JvmStatic
    fun isValidColor(primaryColor: String?): Boolean {
        if (primaryColor == null) return false
        return try {
            Color.parseColor(primaryColor)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun checkSystemColor(color: String): String {
        if (isValidColor(color)) return color
        return try {
            if (color.startsWith("color_")) {
                val idColor = color.replace("color_", "")
                val colorResField = android.R.color::class.java.getField(idColor)
                val colorRes = colorResField.getInt(null)
                if (colorRes != -1) {
                    "#" + Integer.toHexString(ContextCompat.getColor(Utils.getApplication(), colorRes))
                } else "0"
            } else "0"
        } catch (e: Exception) {
            "0"
        }
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth <= 0) 1 else drawable.intrinsicWidth
        val height = if (drawable.intrinsicHeight <= 0) 1 else drawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @JvmStatic
    fun getDominantColor(bitmap: Bitmap): Int {
        val colorCountMap = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 0) {
                    colorCountMap[color] = (colorCountMap[color] ?: 0) + 1
                }
            }
        }
        return colorCountMap.entries.maxByOrNull { it.value }?.key ?: Color.BLACK
    }

    @JvmStatic
    fun colorDistance(color1: Int, color2: Int): Double {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        return Math.sqrt(Math.pow((r1 - r2).toDouble(), 2.0) + Math.pow((g1 - g2).toDouble(), 2.0) + Math.pow((b1 - b2).toDouble(), 2.0))
    }

    @JvmStatic
    fun replaceColor(bitmap: Bitmap, oldColor: Int, newColor: Int, threshold: Double): Bitmap {
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until newBitmap.height) {
            for (x in 0 until newBitmap.width) {
                val currentColor = newBitmap.getPixel(x, y)
                if (colorDistance(currentColor, oldColor) < threshold) {
                    newBitmap.setPixel(x, y, newColor)
                }
            }
        }
        return newBitmap
    }

    @JvmStatic
    fun resizeDrawable(icon: Drawable, width: Int, height: Int): Drawable {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, canvas.width, canvas.height)
        icon.draw(canvas)
        return BitmapDrawable(Utils.getApplication().resources, bitmap)
    }
}
