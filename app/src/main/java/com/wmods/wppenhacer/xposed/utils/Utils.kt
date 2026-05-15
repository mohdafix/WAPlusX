package com.wmods.wppenhacer.xposed.utils

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.WppXposed
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object Utils {

    private const val TAG_KEY = "wae_ticker_name"
    private val spamMap = HashMap<String, Long>()

    @JvmStatic
    fun isSpam(key: String, throttle: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = spamMap[key] ?: 0L
        if (now - last < throttle) return true
        spamMap[key] = now
        return false
    }

    @JvmStatic
    fun tagDrawable(d: Drawable?, name: String) {
        if (d == null) return
        XposedHelpers.setAdditionalInstanceField(d, TAG_KEY, name)
        d.constantState?.let {
            XposedHelpers.setAdditionalInstanceField(it, TAG_KEY, name)
        }
    }

    @JvmStatic
    fun getTickerName(d: Drawable?): String? {
        var current = d
        var depth = 0
        while (current != null && depth < 10) {
            depth++
            val tag = XposedHelpers.getAdditionalInstanceField(current, TAG_KEY)
            if (tag is String) return tag

            try {
                current.constantState?.let {
                    val csTag = XposedHelpers.getAdditionalInstanceField(it, TAG_KEY)
                    if (csTag is String) return csTag
                }
            } catch (ignored: Throwable) {
            }

            when (current) {
                is LayerDrawable -> {
                    for (i in 0 until current.numberOfLayers) {
                        getTickerName(current.getDrawable(i))?.let { return it }
                    }
                    return null
                }
                is DrawableWrapper -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        current = current.drawable
                    } else {
                        return null
                    }
                }
                is StateListDrawable -> {
                    current = try {
                        current.current
                    } catch (t: Throwable) {
                        null
                    }
                }
                else -> {
                    try {
                        val inner = try {
                            XposedHelpers.callMethod(current, "getDrawable") as? Drawable
                        } catch (t1: Throwable) {
                            XposedHelpers.callMethod(current, "getWrappedDrawable") as? Drawable
                        }
                        if (inner == null || inner === current) return null
                        current = inner
                    } catch (t: Throwable) {
                        return null
                    }
                }
            }
        }
        return null
    }

    @JvmStatic
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("WaEnhancer", text)
        clipboard?.setPrimaryClip(clip)
    }

    @JvmField
    val executorService: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    @JvmField
    var xprefs: XSharedPreferences? = null

    private val ids = HashMap<String, Int>()

    @JvmStatic
    fun init(loader: ClassLoader?, pref: XSharedPreferences?) {
        xprefs = pref
        val context = getApplication()
        val notificationManager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("wppenhacer", "WAE Enhancer", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
    }

    @JvmStatic
    fun getApplication(): Application {
        return FeatureLoader.mApp ?: App.getInstance()
    }

    @JvmStatic
    fun getExecutor(): ExecutorService = executorService

    @JvmStatic
    fun postToMainThread(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    @JvmStatic
    fun doRestart(context: Context): Boolean {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        val componentName = intent.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        mainIntent.setPackage(context.packageName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
        return true
    }

    @SuppressLint("DiscouragedApi")
    @JvmStatic
    fun getID(name: String?, type: String?): Int {
        if (name.isNullOrEmpty() || type.isNullOrEmpty()) return -1

        val key = "${type}_$name"
        synchronized(ids) {
            ids[key]?.let { return it }
        }

        return try {
            val app = getApplication()
            val context = app.applicationContext
            var id = context.resources.getIdentifier(name, type, app.packageName)
            if (id == 0) {
                // Try com.whatsapp if app.packageName is different
                id = context.resources.getIdentifier(name, type, "com.whatsapp")
            }
            if (id == 0) {
                // Try com.whatsapp.w4b
                id = context.resources.getIdentifier(name, type, "com.whatsapp.w4b")
            }

            if (id == 0 && type == "id" && name.startsWith("wae_")) {
                id = 0x7e000000 + Math.abs(name.hashCode() % 0xFFFF)
            }

            if (xprefs?.getBoolean("enablelogs", true) == true) {
                 XposedBridge.log("Utils.getID: $type/$name -> $id (pkg: ${app.packageName})")
            }

            synchronized(ids) {
                ids[key] = id
            }
            id
        } catch (e: Exception) {
            XposedBridge.log("Error getting resource ID: type=$type, name=$name, error: ${e.message}")
            -1
        }
    }

    @JvmStatic
    fun dipToPixels(dipValue: Float): Int {
        val metrics = getApplication().resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics).toInt()
    }

    @JvmStatic
    fun getMyNumber(): String {
        val app = getApplication()
        return app.getSharedPreferences("${app.packageName}_preferences_light", Context.MODE_PRIVATE)
            .getString("ph", "") ?: ""
    }

    @JvmStatic
    fun getDateTimeFromMillis(timestamp: Long): String {
        return SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))
    }

    @SuppressLint("SdCardPath")
    @JvmStatic
    fun getDestination(name: String): String {
        if (xprefs?.getBoolean("lite_mode", false) == true) {
            val folder = WppCore.getPrivString("download_folder", null) ?: throw Exception("Download Folder is not selected!")
            val documentFile = DocumentFile.fromTreeUri(getApplication(), Uri.parse(folder))
            val wppFolder = getURIFolderByName(documentFile, "WhatsApp", true)
            getURIFolderByName(wppFolder, name, true) ?: throw Exception("Folder not found!")
            return "$folder/WhatsApp/$name"
        }
        val folder = WppXposed.getPref().getString("download_local", "/sdcard/Download") ?: "/sdcard/Download"
        val waFolder = File(folder, "WhatsApp")
        val filePath = File(waFolder, name)
        try {
            WppCore.getClientBridge()?.createDir(filePath.absolutePath)
        } catch (ignored: Exception) {
        }
        return "${filePath.absolutePath}/"
    }

    @JvmStatic
    fun getURIFolderByName(documentFile: DocumentFile?, folderName: String, createDir: Boolean): DocumentFile? {
        if (documentFile == null) return null
        documentFile.listFiles().firstOrNull { it.name == folderName }?.let { return it }
        return if (createDir) documentFile.createDirectory(folderName) else null
    }

    @JvmStatic
    fun copyFile(srcFile: File?, destFolder: String, name: String): String {
        if (srcFile == null || !srcFile.exists()) return "File not found or is null"

        if (xprefs?.getBoolean("lite_mode", false) == true) {
            return try {
                val folder = WppCore.getPrivString("download_folder", null) ?: return "Root folder not selected"
                var documentFolder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(folder)) ?: return "Invalid root URI"
                val relativeDest = destFolder.replace("$folder/", "")
                for (f in relativeDest.split("/")) {
                    if (f.isEmpty()) continue
                    documentFolder = getURIFolderByName(documentFolder, f, false) ?: return "Failed to get folder: $f"
                }
                val newFile = documentFolder.createFile("*/*", name) ?: return "Failed to create destination file"

                getApplication().contentResolver.openOutputStream(newFile.uri)?.use { out ->
                    FileInputStream(srcFile).use { `in` ->
                        `in`.copyTo(out)
                    }
                    ""
                } ?: "Failed to open output stream"
            } catch (e: Exception) {
                XposedBridge.log(e)
                e.message ?: "Unknown error"
            }
            } else {
                val destFile = File(destFolder, name)
                return try {
                    WppCore.getClientBridge()?.openFile(destFile.absolutePath, true)?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { out ->
                            FileInputStream(srcFile).use { `in` ->
                                `in`.copyTo(out)
                            }
                        }
                    }
                    scanFile(destFile)
                    ""
            } catch (e: Exception) {
                XposedBridge.log(e)
                e.message ?: "Unknown error"
            }
        }
    }

    @JvmStatic
    fun copyFile(`in`: InputStream?, destFolder: String, name: String): String {
        if (`in` == null) return "Input stream is null"

        return try {
            if (xprefs?.getBoolean("lite_mode", false) == true) {
                val folder = WppCore.getPrivString("download_folder", null) ?: return "Root folder not selected"
                var documentFolder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(folder)) ?: return "Invalid root URI"
                val relativeDest = destFolder.replace("$folder/", "")
                for (f in relativeDest.split("/")) {
                    if (f.isEmpty()) continue
                    documentFolder = getURIFolderByName(documentFolder, f, false) ?: return "Failed to get folder: $f"
                }
                val newFile = documentFolder.createFile("*/*", name) ?: return "Failed to create destination file"

                getApplication().contentResolver.openOutputStream(newFile.uri)?.use { out ->
                    `in`.use { it.copyTo(out) }
                    ""
                } ?: "Failed to open output stream"
            } else {
                val destFile = File(destFolder, name)
                WppCore.getClientBridge()?.openFile(destFile.absolutePath, true)?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        `in`.use { it.copyTo(out) }
                    }
                }
                scanFile(destFile)
                ""
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
            e.message ?: "Unknown error"
        }
    }

    @JvmStatic
    fun showToast(message: String, length: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performShowToast(message, length)
        } else {
            Handler(Looper.getMainLooper()).post { performShowToast(message, length) }
        }
    }

    private fun performShowToast(message: String, length: Int) {
        val enhancedEnabled = xprefs?.getBoolean("enhanced_toast_enabled", false) ?: false
        if (enhancedEnabled) {
            showEnhancedToast(message, length)
        } else {
            Toast.makeText(getApplication(), message, length).show()
        }
    }

    private fun showEnhancedToast(message: String, length: Int) {
        try {
            val context = getApplication()
            val toast = Toast(context)

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 20, 32, 20)
                gravity = Gravity.CENTER_VERTICAL
            }

            val backgroundColor = DesignUtils.getPrimarySurfaceColor()
            val textColor = DesignUtils.getPrimaryTextColor()
            var accentColor = xprefs?.getInt("primary_color", textColor) ?: textColor
            if (accentColor == 0) accentColor = textColor

            layout.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 32f
                setColor(backgroundColor)
                setStroke(2, accentColor)
            }

            val iconView = ImageView(context).apply {
                val iconSize = (28 * context.resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = 16
                }
                setImageResource(android.R.drawable.ic_dialog_info)
                setColorFilter(accentColor)
            }
            layout.addView(iconView)

            val messageView = TextView(context).apply {
                text = message
                setTextColor(textColor)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }
            layout.addView(messageView)

            toast.view = layout
            toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 150)
            toast.duration = length
            toast.show()
        } catch (e: Exception) {
            Toast.makeText(getApplication(), message, length).show()
        }
    }

    @JvmStatic
    fun setToClipboard(string: String) {
        val clipboard = getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", string)
        clipboard.setPrimaryClip(clip)
    }

    @JvmStatic
    fun generateName(userJid: FMessageWpp.UserJid, fileFormat: String): String {
        val contactName = WppCore.getContactName(userJid)
        val number = userJid.phoneRawString ?: ""
        val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "${toValidFileName(contactName)}_${number}_$time.$fileFormat"
    }

    @JvmStatic
    fun toValidFileName(input: String): String {
        return input.replace("[:\\\\/*\"?|<>']".toRegex(), " ")
    }

    @JvmStatic
    fun scanFile(file: File) {
        MediaScannerConnection.scanFile(
            getApplication(),
            arrayOf(file.absolutePath),
            arrayOf(MimeTypeUtils.getMimeTypeFromExtension(file.absolutePath)),
            null
        )
    }

    @JvmStatic
    fun getProperties(prefs: XSharedPreferences, key: String, checkKey: String?): Properties {
        val properties = Properties()
        if (checkKey != null && !prefs.getBoolean(checkKey, false)) return properties
        val text = prefs.getString(key, "") ?: ""
        val pattern = Pattern.compile("^/\\*\\s*(.*?)\\s*\\*/", Pattern.DOTALL)
        val matcher = pattern.matcher(text)

        if (matcher.find()) {
            val propertiesText = matcher.group(1) ?: ""
            val lines = propertiesText.split("\\s*\\n\\s*".toRegex())

            for (line in lines) {
                val keyValue = line.split("\\s*=\\s*".toRegex())
                if (keyValue.size < 2) continue
                val skey = keyValue[0].trim()
                val value = keyValue[1].trim().replace("^\"|\"$".toRegex(), "")
                properties[skey] = value
            }
        }
        return properties
    }

    @JvmStatic
    fun tryParseInt(value: String?, default: Int): Int {
        return value?.trim()?.toIntOrNull() ?: default
    }

    @SuppressLint("PrivateApi")
    @JvmStatic
    fun getApplicationByReflect(): Application {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val thread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val app = activityThreadClass.getMethod("getApplication").invoke(thread) as? Application
            app ?: throw NullPointerException("u should init first")
        } catch (e: Exception) {
            e.printStackTrace()
            throw NullPointerException("u should init first")
        }
    }

    @JvmStatic
    fun <T> binderLocalScope(block: BinderLocalScopeBlock<T>): T {
        val identity = Binder.clearCallingIdentity()
        return try {
            block.execute()
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @JvmStatic
    fun getActivity(context: Context?): Activity? {
        var current = context
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: break
        }
        return null
    }

    @JvmStatic
    fun getAuthorFromCss(code: String?): String? {
        if (code == null) return null
        val match = Pattern.compile("author\\s*=\\s*(.*?)\n").matcher(code)
        return if (match.find()) match.group(1) else null
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showNotification(title: String, content: String) {
        showNotification(title, content, null)
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showNotification(title: String, content: String, @Nullable contentIntent: PendingIntent?) {
        showNotification(title, content, contentIntent, null, Random().nextInt(), null)
    }

    private val groupCounts = HashMap<String, Int>()

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showNotification(title: String, content: String, @Nullable contentIntent: PendingIntent?, tag: String?, id: Int, groupKey: String?) {
        showNotification(title, content, contentIntent, tag, id, groupKey, null)
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showNotification(title: String, content: String, @Nullable contentIntent: PendingIntent?, tag: String?, id: Int, groupKey: String?, @Nullable style: NotificationCompat.Style?) {
        showNotification(title, content, contentIntent, tag, id, groupKey, style, null)
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showNotification(title: String, content: String, @Nullable contentIntent: PendingIntent?, tag: String?, id: Int, groupKey: String?, @Nullable style: NotificationCompat.Style?, @Nullable actions: Array<NotificationCompat.Action>?) {
        try {
            val context = getApplication()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val channelId = "wppenhacer"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(channelId, "WAE Enhancer", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Notifications from WA Enhancer"
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }

            val iconRes = android.R.drawable.ic_dialog_info
            val notificationBuilder = NotificationCompat.Builder(context, channelId).apply {
                setSmallIcon(iconRes)
                setContentTitle(title)
                setContentText(content)
                setContentIntent(contentIntent)
                setAutoCancel(true)
                priority = NotificationCompat.PRIORITY_HIGH
                setDefaults(NotificationCompat.DEFAULT_ALL)
                setStyle(style ?: NotificationCompat.BigTextStyle().bigText(content))
                actions?.forEach { addAction(it) }
            }

            if (groupKey != null) {
                notificationBuilder.setGroup(groupKey)
                val count = synchronized(groupCounts) {
                    val c = (groupCounts[groupKey] ?: 0) + 1
                    groupCounts[groupKey] = c
                    c
                }

                val summaryTitle = if (groupKey == "antirevoke_group") {
                    context.getString(ResId.string.deleted_messages)
                } else {
                    title
                }
                val summaryText = "$count ${if (count == 1) "update" else "updates"}"

                val summaryBuilder = NotificationCompat.Builder(context, channelId).apply {
                    setSmallIcon(iconRes)
                    setContentTitle(summaryTitle)
                    setContentText(summaryText)
                    setGroup(groupKey)
                    setGroupSummary(true)
                    setAutoCancel(true)
                }
                notificationManager.notify(groupKey.hashCode(), summaryBuilder.build())
            }

            notificationManager.notify(tag, id, notificationBuilder.build())
        } catch (e: Exception) {
            XposedBridge.log("WaEnhancer: Error showing notification: ${e.message}")
        }
    }

    @JvmStatic
    fun cancelNotification(tag: String?, id: Int) {
        try {
            val notificationManager = getApplication().getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(tag, id)
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun clearGroupCount(groupKey: String) {
        synchronized(groupCounts) {
            groupCounts.remove(groupKey)
        }
    }

    @JvmStatic
    fun cancelNotification(id: Int) {
        cancelNotification(null, id)
    }

    @JvmStatic
    fun openLink(activity: Activity, url: String) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    @JvmStatic
    fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val rs = RenderScript.create(context)
        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val inputAlloc = Allocation.createFromBitmap(rs, bitmap)
        val outputAlloc = Allocation.createFromBitmap(rs, output)
        blur.setRadius(radius)
        blur.setInput(inputAlloc)
        blur.forEach(outputAlloc)
        outputAlloc.copyTo(output)
        rs.destroy()
        return output
    }

    @JvmStatic
    fun isRooted(): Boolean {
        val rootPackages = arrayOf(
            "me.bmax.apatch", "me.weishu.kernelsu", "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk", "eu.chainfire.supersu", "com.noshufou.android.su"
        )
        val pm = getApplication().packageManager
        for (pkg in rootPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }

        val rootDirs = arrayOf("/data/adb/ap", "/data/adb/ksu", "/data/adb/magisk", "/data/adb/modules")
        for (dir in rootDirs) {
            if (File(dir).exists()) return true
        }

        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo", "root"))
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) true else {
                process.destroy()
                false
            }
        } catch (ignored: Exception) {
            false
        }
    }

    @FunctionalInterface
    interface BinderLocalScopeBlock<T> {
        fun execute(): T
    }
}
