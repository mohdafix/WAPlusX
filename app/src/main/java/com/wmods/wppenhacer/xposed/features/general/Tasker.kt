package com.wmods.wppenhacer.xposed.features.general

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.*

class Tasker(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        @JvmStatic
        var taskerEnabled: Boolean = false
        
        @Volatile
        @JvmStatic
        var lastNotificationReplyTime: Long = 0

        @JvmStatic
        fun sendTaskerEvent(name: String, number: String, event: String) {
            if (!taskerEnabled) return

            val intent = Intent("com.waenhancer.EVENT")
            intent.putExtra("name", name)
            intent.putExtra("number", number)
            intent.putExtra("event", event)
            Utils.getApplication().sendBroadcast(intent)
        }

        private fun logEventViaProvider(context: Context, type: String, targetNumber: String, messagePreview: String) {
            try {
                val uri = Uri.parse("content://com.wmods.wppenhacer.provider")
                val extras = android.os.Bundle()
                extras.putString("type", type)
                extras.putString("targetNumber", targetNumber)
                extras.putString("messagePreview", messagePreview)
                context.contentResolver.call(uri, "log_tasker_event", null, extras)
            } catch (t: Throwable) {
                // Ignore if provider not ready
            }
        }
    }

    override fun doHook() {
        taskerEnabled = prefs.getBoolean("tasker", false)
        
        try {
            // Always register the broadcast receiver so Scheduled Messages can use it
            registerSenderMessage()
            
            if (!taskerEnabled) return
            
            hookReceiveMessage()
            hookConversationActivity()
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun registerSenderMessage() {
        val filter = IntentFilter()
        filter.addAction("com.waenhancer.MESSAGE_SENT")
        filter.addAction("com.waenhancer.MESSAGE_SENT_INTERNAL")
        ContextCompat.registerReceiver(
            Utils.getApplication(),
            SenderMessageBroadcastReceiver(),
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        
        // Also hook the statically registered PhoneIdRequestReceiver to wake up WhatsApp
        // reliably for scheduled messages even if the app was killed.
        try {
            XposedHelpers.findAndHookMethod(
                "com.whatsapp.phoneid.PhoneIdRequestReceiver",
                classLoader,
                "onReceive",
                Context::class.java,
                Intent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as Context
                        val intent = param.args[1] as? Intent ?: return
                        if (intent.getBooleanExtra("wae_schedule_payload", false)) {
                            // Cancel original PhoneId processing
                            param.result = null
                            
                            XposedBridge.log("[WAE] Intercepted PhoneIdRequestReceiver for scheduled message")
                            // Forward to our receiver logic
                            SenderMessageBroadcastReceiver().onReceive(context, intent)
                        }
                    }
                }
            )
            XposedBridge.log("[WAE] Successfully hooked PhoneIdRequestReceiver for scheduled messages")
        } catch (t: Throwable) {
            XposedBridge.log("[WAE] Failed to hook PhoneIdRequestReceiver: " + t.message)
        }
    }

    private fun hookReceiveMessage() {
        val method = Unobfuscator.loadReceiptMethod(classLoader)

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid")
                    val userJidObject = ReflectionUtils.getArg(param.args, jidClass, 0) ?: return

                    val strings = ReflectionUtils.findClassesOfType((param.method as java.lang.reflect.Method).parameterTypes, String::class.java)
                    if (strings.isEmpty()) return

                    val msgTypeIdx = strings.last().first
                    if (msgTypeIdx < param.args.size && "sender" == param.args[msgTypeIdx]) {
                        return
                    }

                    var keyMessage: FMessageWpp.Key? = null
                    val keyObject = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0)
                    if (keyObject != null) {
                        keyMessage = FMessageWpp.Key(keyObject)
                    } else if (strings.size >= 2) {
                        val msgIdIdx = strings[0].first
                        if (msgIdIdx < param.args.size) {
                            val idMessage = param.args[msgIdIdx] as String
                            val userJid = FMessageWpp.UserJid(userJidObject)
                            keyMessage = FMessageWpp.Key(idMessage, userJid, false)
                        }
                    }

                    if (keyMessage == null) return

                    val fMessage = keyMessage.fMessage ?: return
                    val userJid = fMessage.key.remoteJid
                    if (userJid.isNull || userJid.isStatus) return

                    val name = WppCore.getContactName(userJid)
                    val number = userJid.phoneNumber ?: ""
                    val msg = fMessage.messageStr

                    if (TextUtils.isEmpty(msg) || TextUtils.isEmpty(number)) return

                    Handler(Looper.getMainLooper()).post {
                        logEventViaProvider(Utils.getApplication(), "INCOMING", number, msg ?: "")

                        val intent = Intent("com.waenhancer.MESSAGE_RECEIVED")
                        intent.putExtra("number", number)
                        intent.putExtra("name", name)
                        intent.putExtra("message", msg)
                        Utils.getApplication().sendBroadcast(intent)
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("[WAE] Tasker receive message hook error: " + t.message)
                }
            }
        })
    }

    private fun hookConversationActivity() {
        try {
            XposedHelpers.findAndHookMethod(
                "com.whatsapp.Conversation",
                classLoader,
                "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as android.app.Activity
                        val intent = activity.intent
                        if (intent != null && intent.hasExtra("wae_auto_send_message")) {
                            val msgToSend = intent.getStringExtra("wae_auto_send_message") ?: return
                            
                            activity.overridePendingTransition(0, 0)
                            activity.window.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            )
                            activity.window.setDimAmount(0f)
                            
                            autoSendTextAndFinish(activity, msgToSend, 15)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[WAE] Failed to hook Conversation onCreate: " + t.message)
        }
    }

    private fun autoSendTextAndFinish(activity: android.app.Activity, message: String, attemptsLeft: Int) {
        if (attemptsLeft <= 0) {
            finishActivitySilently(activity)
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (activity.isFinishing) return@postDelayed
                val decorView = activity.window.decorView
                
                val inputField = findMessageInput(decorView)
                if (inputField == null) {
                    autoSendTextAndFinish(activity, message, attemptsLeft - 1)
                    return@postDelayed
                }

                inputField.setText(message)
                inputField.setSelection(message.length)

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (activity.isFinishing) return@postDelayed
                        val sendBtn = findSendButton(activity, decorView)
                        if (sendBtn != null && sendBtn.isEnabled && sendBtn.visibility == android.view.View.VISIBLE) {
                            sendBtn.performClick()
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                finishActivitySilently(activity)
                            }, 300)
                        } else {
                            autoSendTextAndFinish(activity, message, attemptsLeft - 1)
                        }
                    } catch (e: Exception) {
                        autoSendTextAndFinish(activity, message, attemptsLeft - 1)
                    }
                }, 300)

            } catch (e: Exception) {
                autoSendTextAndFinish(activity, message, attemptsLeft - 1)
            }
        }, 400)
    }

    private fun findMessageInput(root: android.view.View?): android.widget.EditText? {
        if (root == null) return null
        if (root is android.widget.EditText) {
            val hint = root.hint
            if (hint != null && (hint.toString().lowercase().contains("message") || hint.toString().lowercase().contains("type"))) {
                return root
            }
            return root
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findMessageInput(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun findSendButton(activity: android.app.Activity, root: android.view.View?): android.view.View? {
        if (root == null) return null
        try {
            val sendId = activity.resources.getIdentifier("send", "id", activity.packageName)
            if (sendId != 0) {
                val v = activity.findViewById<android.view.View>(sendId)
                if (v != null && v.visibility == android.view.View.VISIBLE) {
                    return v
                }
            }
        } catch (ignored: Exception) {
        }
        return findSendButtonRecursive(root)
    }

    private fun findSendButtonRecursive(root: android.view.View?): android.view.View? {
        if (root == null) return null
        try {
            val desc = root.contentDescription
            if (desc != null) {
                val d = desc.toString().lowercase()
                if (d.contains("send") || d.contains("submit")) return root
            }
            if (root.id != android.view.View.NO_ID) {
                val idName = root.resources.getResourceEntryName(root.id)
                if (idName != null && idName.lowercase().contains("send")) return root
            }
        } catch (ignored: Exception) {
        }
        
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findSendButtonRecursive(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun finishActivitySilently(activity: android.app.Activity) {
        if (activity.isFinishing) return
        try {
            activity.moveTaskToBack(true)
        } catch (ignored: Exception) {
        }
        activity.finishAndRemoveTask()
        activity.overridePendingTransition(0, 0)
    }

    class SenderMessageBroadcastReceiver : BroadcastReceiver() {
        companion object {
            private var lastProcessedNumber: String? = null
            private var lastProcessedMessage: String? = null
            private var lastProcessedTime: Long = 0
            private val handler = Handler(Looper.getMainLooper())
            private val pendingQueue = java.util.concurrent.ConcurrentLinkedQueue<PendingMessage>()
        }

        data class PendingMessage(
            val context: Context,
            val intent: Intent,
            val timestamp: Long = System.currentTimeMillis()
        )

        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) return
            handleIntent(context, intent)
        }

        private fun processQueue() {
            val pending = pendingQueue.poll() ?: return
            
            // Discard if older than 5 minutes (300000 ms)
            val messageId = pending.intent.getLongExtra("message_id", -1L)
            if (System.currentTimeMillis() - pending.timestamp > 300000) {
                XposedBridge.log("[WAE] Discarding expired pending message ID $messageId")
                if (messageId != -1L) {
                    sendResultBroadcast(pending.context, messageId, false, "Timeout waiting for WhatsApp sender init")
                }
                // Continue processing remaining queue
                handler.postDelayed({ processQueue() }, 1000)
                return
            }

            XposedBridge.log("[WAE] Retrying queued message ID $messageId")
            handleIntent(pending.context, pending.intent, isRetry = true)
        }

        private fun sendResultBroadcast(context: Context, messageId: Long, success: Boolean, error: String? = null) {
            XposedBridge.log("[WAE] Sending success ack broadcast to WAE for message ID: $messageId (success=$success)")
            val ackIntent = Intent("com.wmods.wppenhacer.MESSAGE_SENT")
            ackIntent.setPackage("com.wmods.wppenhacer")
            ackIntent.putExtra("message_id", messageId)
            ackIntent.putExtra("success", success)
            if (error != null) ackIntent.putExtra("error_reason", error)
            context.sendBroadcast(ackIntent)
        }

        private fun handleIntent(context: Context, intent: Intent, isRetry: Boolean = false) {
            var rawNumber = intent.getStringExtra("number")
            val message = intent.getStringExtra("message")
            val messageId = intent.getLongExtra("message_id", -1L)
            val mediaPath = intent.getStringExtra("media_path")

            XposedBridge.log("[WAE] SenderMessageBroadcastReceiver handling intent: rawNumber=$rawNumber, message=$message, messageId=$messageId, mediaPath=$mediaPath, isRetry=$isRetry")

            if (rawNumber == null || (message == null && mediaPath == null)) {
                XposedBridge.log("[WAE] SenderMessageBroadcastReceiver ignored broadcast: rawNumber or message is null")
                return
            }

            val number = if (rawNumber.contains("@")) rawNumber else rawNumber.replace("\\D".toRegex(), "")

            val now = System.currentTimeMillis()
            if (!isRetry && number == lastProcessedNumber && message == lastProcessedMessage && now - lastProcessedTime < 2000) {
                XposedBridge.log("[WAE] SenderMessageBroadcastReceiver ignored broadcast: duplicate within 2 seconds")
                return
            }
            if (!isRetry) {
                lastProcessedNumber = number
                lastProcessedMessage = message
                lastProcessedTime = now
            }

            if (message != null) {
                logEventViaProvider(context, "OUTGOING", number, message)
            }

            XposedBridge.log("[WAE] SenderMessageBroadcastReceiver calling WppCore message dispatcher: number=$number")
            val sent = try {
                if (mediaPath.isNullOrEmpty()) {
                    if (message.isNullOrEmpty()) false else WppCore.sendMessage(number, message)
                } else {
                    WppCore.sendMediaMessage(number, message ?: "", mediaPath)
                }
            } catch (e: Exception) {
                XposedBridge.log("[WAE] Exception in dispatcher: ${e.message}")
                false
            }
            
            XposedBridge.log("[WAE] WppCore message dispatcher result: $sent")

            if (sent) {
                lastNotificationReplyTime = System.currentTimeMillis()

                if (messageId != -1L) {
                    sendResultBroadcast(context, messageId, true)
                }
                
                // Process next in queue if any
                if (!pendingQueue.isEmpty()) {
                    handler.postDelayed({ processQueue() }, 2000)
                }
            } else {
                if (messageId != -1L) {
                    // For scheduled messages, queue and retry (up to 5 mins)
                    XposedBridge.log("[WAE] WppCore failed. Senders likely not ready. Queuing message ID: $messageId")
                    pendingQueue.add(PendingMessage(context, intent))
                    handler.postDelayed({ processQueue() }, 61000)
                } else {
                    XposedBridge.log("[WAE] WppCore.sendMessage failed, launching Conversation fallback")
                    try {
                        val activityIntent = Intent()
                        activityIntent.setClassName(context.packageName, "com.whatsapp.Conversation")
                        val jid = if (number.contains("@")) number else "$number@s.whatsapp.net"
                        activityIntent.putExtra("jid", jid)
                        activityIntent.putExtra("wae_auto_send_message", message)
                        activityIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        context.startActivity(activityIntent)
                    } catch (e: Exception) {
                        XposedBridge.log("[WAE] Failed to launch Conversation fallback: $e")
                    }
                }
            }
        }
    }

    override fun getPluginName(): String = "Tasker"
}
