package com.wmods.wppenhacer.xposed.features.general

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.Executors

class MessageBomber(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    private val executor = Executors.newSingleThreadExecutor()

    override fun doHook() {
        if (!prefs.getBoolean("message_bomber", false)) return

        try {
            val textSenderMethod = Unobfuscator.loadUserActionsTextMessageSending(classLoader)
            if (textSenderMethod == null) {
                logDebug("MessageBomber: TextSenderMethod is null")
                return
            }

            XposedBridge.hookMethod(textSenderMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val index = ReflectionUtils.findIndexOfType(param.args, String::class.java)
                    if (index == -1) return

                    val message = param.args[index] as? String ?: return
                    if (message.startsWith(".bomb ", ignoreCase = true)) {
                        // Cancel original send to prevent sending the ".bomb" command itself
                        param.result = null

                        val parts = message.split(" ", limit = 3)
                        if (parts.size >= 3) {
                            val count = parts[1].toIntOrNull() ?: return
                            val textToBomb = parts[2]
                            
                            val method = param.method
                            val thisObject = param.thisObject
                            val args = param.args.clone()
                            args[index] = textToBomb

                            // Execute bomber on background thread to prevent UI lockup
                            executor.submit {
                                for (i in 0 until count) {
                                    try {
                                        XposedBridge.invokeOriginalMethod(method, thisObject, args)
                                        Thread.sleep(100) // Small delay to prevent crashing WhatsApp backend completely
                                    } catch (e: Exception) {
                                        logDebug("Bomber invoke error", e)
                                    }
                                }
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            logDebug("Failed to hook MessageBomber", e)
        }
    }

    override fun getPluginName(): String {
        return "Message Bomber"
    }
}
