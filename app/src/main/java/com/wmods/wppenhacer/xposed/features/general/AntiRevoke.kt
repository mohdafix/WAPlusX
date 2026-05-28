package com.wmods.wppenhacer.xposed.features.general

import android.app.Activity
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FStatusWpp
import com.wmods.wppenhacer.xposed.core.components.ProtocolTreeNodeWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Modifier
import java.text.DateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class AntiRevoke(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    companion object {
        private val messageRevokedMap = ConcurrentHashMap<String, MutableSet<String>>()
        
        private val dateFormatThreadLocal = ThreadLocal.withInitial {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, 
                Utils.getApplication().resources.configuration.locales.get(0))
        }

        fun findObjectFMessage(param: XC_MethodHook.MethodHookParam): FMessageWpp? {
            val args = param.args ?: return null
            
            // Try every arg as a potential FMessage or container (using new automatic unwrapping)
            for (i in args.indices) {
                val arg = args[i] ?: continue
                try {
                    val fMsg = FMessageWpp(arg)
                    if (fMsg.isValid) {
                        XposedBridge.log("AntiRevoke: Found message in Arg[$i] (${arg.javaClass.name})")
                        return fMsg
                    }
                } catch (e: Exception) {}
            }

            // Fallback to StatusData/FStatus in args (via MenuStatusListener)
            for (i in args.indices) {
                val arg = args[i] ?: continue
                try {
                    val fMsg = MenuStatusListener.getFMessageFromStatusData(arg)
                    if (fMsg != null && fMsg.isValid) {
                        XposedBridge.log("AntiRevoke: Found message via StatusData in Arg[$i] (${arg.javaClass.name})")
                        return fMsg
                    }
                } catch (e: Exception) {}
            }

            // Final fallback to MenuStatusListener cache
            try {
                val index = MenuStatusListener.currentIndex
                if (index >= 0 && index < MenuStatusListener.currentStatusList.size) {
                    val fMsg = MenuStatusListener.currentStatusList[index]
                    if (fMsg.isValid) {
                        XposedBridge.log("AntiRevoke: Found message via MenuStatusListener cache at index $index")
                        return fMsg
                    }
                }
            } catch (e: Exception) {}

            return null
        }

        private fun getRevokedMessagesForJid(fMessage: FMessageWpp): MutableSet<String> {
            val key = fMessage.key
            val remoteJid = key.remoteJid
            
            val isStatus = remoteJid.isStatus
            val jidKey = if (isStatus) {
                fMessage.userJid.phoneNumber ?: "status"
            } else {
                remoteJid.phoneNumber
            }

            if (jidKey == null) return Collections.synchronizedSet(HashSet<String>())

            return messageRevokedMap.getOrPut(jidKey) {
                val dbList = DelMessageStore.getInstance(Utils.getApplication()).getMessagesByJid(jidKey)
                XposedBridge.log("AntiRevoke: Loaded ${dbList?.size ?: 0} revoked messages from DB for $jidKey")
                Collections.synchronizedSet(dbList ?: HashSet<String>()) as MutableSet<String>
            }
        }

        private fun persistRevokedMessage(fMessage: FMessageWpp, messageID: String) {
            val remoteJid = fMessage.key.remoteJid
            val isStatus = remoteJid.isStatus
            val stripJID = if (isStatus) {
                fMessage.userJid.phoneNumber ?: "status"
            } else {
                remoteJid.phoneNumber
            }

            if (stripJID == null) return

            XposedBridge.log("AntiRevoke: persistRevokedMessage JID=$stripJID ID=$messageID isStatus=$isStatus")
            getRevokedMessagesForJid(fMessage).add(messageID)
            DelMessageStore.getInstance(Utils.getApplication()).insertMessage(stripJID, messageID, System.currentTimeMillis())
        }
    }

    @Throws(Exception::class)
    override fun doHook() {
        val unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader)
        val statusPlaybackViewClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader)
        val antiRevokeFStatusMethod = Unobfuscator.loadAntiRevokeFStatusMethod(classLoader)

        // Status Revocation Hook
        XposedBridge.hookMethod(antiRevokeFStatusMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val args = param.args ?: return
                    val fStatusKeyObj = args.getOrNull(1) ?: args.getOrNull(0) ?: return
                    val fStatusKey = FStatusWpp.FStatusKey(fStatusKeyObj)
                    val fstatus = fStatusKey.fStatus ?: return
                    val fMessage = fstatus.fMessage ?: return
                    
                    if (!fStatusKey.isFromMe) {
                        if (handleRevocationAttempt(fMessage, fStatusKey.messageID) != 0) {
                            param.result = 0 // Skip revocation
                        }
                    }
                } catch (e: Exception) {
                    XposedBridge.log("AntiRevoke: Error in status hook: $e")
                }
            }
        })

        // Message Revocation Hook
        val antiRevokeMessageMethods = Unobfuscator.loadAntiRevokeMessageMethod(classLoader)
        for (method in antiRevokeMessageMethods) {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val args = param.args ?: return
                        val fMessageObj = ReflectionUtils.getArg(args, FMessageWpp.TYPE, 0) ?: return
                        val fMessage = FMessageWpp(fMessageObj)
                        val messageKey = fMessage.key
                        
                        var messageId: String? = null
                        try {
                            messageId = XposedHelpers.getObjectField(fMessageObj, "A01") as? String
                        } catch (e: Exception) {
                            messageId = fMessage.revocationTargetId ?: messageKey.messageID
                        }
                        
                        if (messageId == null) return

                        val remoteJid = messageKey.remoteJid
                        XposedBridge.log("AntiRevoke: Hooked revocation [${param.method.name}] for JID=${remoteJid.phoneNumber}. targetID: $messageId")

                        if (remoteJid.isGroup) {
                            if (fMessage.deviceJid != null && handleRevocationAttempt(fMessage, messageId) != 0) {
                                param.result = true
                            }
                        } else if (!messageKey.isFromMe && handleRevocationAttempt(fMessage, messageId) != 0) {
                            param.result = true
                        }
                    } catch (e: Exception) {
                        XposedBridge.log("AntiRevoke: Error in hook [${param.method.name}]: $e")
                    }
                }
            })
        }

        // Conversation UI Hook
        ConversationItemListener.conversationListeners.add(object : ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(fMessage: FMessageWpp, viewGroup: ViewGroup, position: Int, convertView: View?) {
                val dateTextView = findDateView(viewGroup)
                bindRevokedMessageUI(fMessage, dateTextView, "antirevoke")
            }
        })

        // Status Playback UI Hook
        XposedBridge.hookMethod(unknownStatusPlaybackMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    var fragment = param.thisObject
                    if (fragment == null || !fragment.javaClass.name.contains("StatusPlaybackContactFragment")) {
                        fragment = param.args?.find { it != null && it.javaClass.name.contains("StatusPlaybackContactFragment") }
                    }
                    
                    if (fragment == null) {
                        XposedBridge.log("AntiRevoke: Status playback fragment not found")
                        return
                    }

                    val fMessage = findObjectFMessage(param)
                    if (fMessage == null) {
                        XposedBridge.log("AntiRevoke: Status playback message not found in args")
                        return
                    }

                    val playbackView = ReflectionUtils.findFieldUsingFilterIfExists(fragment.javaClass) { f ->
                        f.type == statusPlaybackViewClass
                    }?.get(fragment)

                    if (playbackView != null) {
                        processStatusPlaybackView(fMessage, playbackView)
                    } else {
                        XposedBridge.log("AntiRevoke: playbackView field not found in fragment")
                    }
                } catch (e: Exception) {
                    XposedBridge.log("AntiRevoke: Status playback UI binding error: $e")
                }
            }
        })

        // Deep Packet Interceptor (Final Safeguard)
        try {
            val ptNodeClass = Unobfuscator.loadProtocolTreeNodeClass(classLoader)
            XposedBridge.hookAllConstructors(ptNodeClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val node = ProtocolTreeNodeWpp(param.thisObject)
                        if (node.tag == "revoke") {
                            val attrs = node.getAttributesMap()
                            val msgId = attrs["id"]
                            val fromJid = attrs["from"]
                            if (msgId != null && fromJid != null) {
                                XposedBridge.log("[AntiRevoke] Intercepted REVOKE packet: ID=$msgId, From=$fromJid")
                                node.tag = "revoke_blocked"
                            }
                        }
                    } catch (e: Exception) {}
                }
            })
        } catch (e: Exception) {}
    }

    private fun processStatusPlaybackView(fMessage: FMessageWpp, objView: Any) {
        val dateId = Utils.getID("date", "id")
        val titleId = Utils.getID("title", "id")
        val foundViews = mutableListOf<TextView>()
        
        // Iterate fields
        ReflectionUtils.getFieldsByType(objView.javaClass, TextView::class.java).forEach { field ->
            try {
                field.isAccessible = true
                val tv = field.get(objView) as? TextView
                if (tv != null) foundViews.add(tv)
            } catch (e: Exception) {}
        }
        
        // Fallback to findViewById if ViewGroup
        if (foundViews.isEmpty() && objView is ViewGroup) {
            if (dateId != 0) (objView.findViewById<View>(dateId) as? TextView)?.let { foundViews.add(it) }
            if (titleId != 0) (objView.findViewById<View>(titleId) as? TextView)?.let { foundViews.add(it) }
        }

        if (foundViews.isEmpty()) {
            findTextViewRecursive(objView as? View ?: return)?.let { foundViews.add(it) }
        }

        for (tv in foundViews) {
            val id = tv.id
            if (id == dateId || id == titleId || id == Utils.getID("date_tv", "id") || id == Utils.getID("timestamp", "id") || id == -1) {
                bindRevokedMessageUI(fMessage, tv, "antirevokestatus")
            }
        }
    }

    private fun findDateView(viewGroup: ViewGroup): TextView? {
        val cached = XposedHelpers.getAdditionalInstanceField(viewGroup, "wae_date_view")
        if (cached is TextView) return cached
        
        val ids = intArrayOf(
            Utils.getID("date", "id"), 
            Utils.getID("date_tv", "id"), 
            Utils.getID("timestamp", "id"),
            Utils.getID("time", "id")
        )
        for (id in ids) {
            if (id != 0) {
                val view = viewGroup.findViewById<View>(id)
                if (view is TextView) {
                    XposedHelpers.setAdditionalInstanceField(viewGroup, "wae_date_view", view)
                    return view
                }
            }
        }
        // Fallback recursive
        val found = findTextViewRecursive(viewGroup)
        if (found != null) {
            XposedHelpers.setAdditionalInstanceField(viewGroup, "wae_date_view", found)
        }
        return found
    }

    private fun bindRevokedMessageUI(fMessage: FMessageWpp, dateTextView: TextView?, antirevokeType: String) {
        if (dateTextView == null) return
        val antirevokeValue = prefs.getString(antirevokeType, "0")?.toIntOrNull() ?: 0
        if (antirevokeValue == 0) {
            val originalMessage = XposedHelpers.getAdditionalInstanceField(dateTextView, "wae_original_text") as? String
            dateTextView.setCompoundDrawables(null, null, null, null)
            if (originalMessage != null) dateTextView.text = originalMessage
            dateTextView.paint.isUnderlineText = false
            dateTextView.setOnClickListener(null)
            return
        }

        val key = fMessage.key
        val messageRevokedList = getRevokedMessagesForJid(fMessage)
        
        val originalMessage = XposedHelpers.getAdditionalInstanceField(dateTextView, "wae_original_text") as? String
        
        var messageID = if (messageRevokedList.contains(key.messageID)) {
            key.messageID
        } else {
            MessageStore.getInstance().getOriginalMessageKey(fMessage.rowId)?.takeIf { messageRevokedList.contains(it) }
        }

        if (messageID == null) {
            dateTextView.setCompoundDrawables(null, null, null, null)
            if (originalMessage != null) dateTextView.text = originalMessage
            dateTextView.paint.isUnderlineText = false
            dateTextView.setOnClickListener(null)
            return
        }

        val timestamp = DelMessageStore.getInstance(Utils.getApplication()).getTimestampByMessageId(messageID)
        if (timestamp > 0) {
            val date = dateFormatThreadLocal.get()?.format(Date(timestamp))
            dateTextView.paint.isUnderlineText = true
            dateTextView.setOnClickListener {
                val label = Utils.getApplication().getString(R.string.message_removed_on)
                Utils.showToast(String.format(label, date), Toast.LENGTH_LONG)
            }
        }

        XposedBridge.log("AntiRevoke: bindRevokedMessageUI type=$antirevokeType value=$antirevokeValue messageID=$messageID")
        when (antirevokeValue) {
            1 -> {
                val deleteNotice = try { Utils.getApplication().getString(R.string.deleted_message) } catch (e: Exception) { "deleted" }
                val currentText = originalMessage ?: dateTextView.text.toString()
                if (originalMessage == null) {
                    XposedHelpers.setAdditionalInstanceField(dateTextView, "wae_original_text", currentText)
                }
                if (!dateTextView.text.contains(deleteNotice)) {
                    dateTextView.text = "$deleteNotice | $currentText"
                }
            }
            2 -> {
                val drawable = try { Utils.getApplication().getDrawable(R.drawable.deleted) } catch (e: Exception) { null }
                dateTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
                dateTextView.compoundDrawablePadding = Utils.dipToPixels(4.0f)
            }
        }
    }

    private fun handleRevocationAttempt(fMessage: FMessageWpp, messageId: String): Int {
        try {
            showRevocationNotification(fMessage)
        } catch (e: Exception) {
            log(e)
        }

        val remoteJid = fMessage.key.remoteJid
        val prefKey = if (remoteJid.isStatus) "antirevokestatus" else "antirevoke"
        val revokeBoolean = prefs.getString(prefKey, "0")?.toIntOrNull() ?: 0
        
        XposedBridge.log("AntiRevoke: handleRevocationAttempt key=$prefKey value=$revokeBoolean")
        if (revokeBoolean == 0) return 0

        val messageRevokedList = getRevokedMessagesForJid(fMessage)
        if (!messageRevokedList.contains(messageId)) {
            CompletableFuture.runAsync {
                try {
                    persistRevokedMessage(fMessage, messageId)
                    val mConversation = WppCore.getCurrentConversation()
                    if (mConversation != null) {
                        val authorPhone = if (remoteJid.isStatus) fMessage.userJid.phoneNumber else remoteJid.phoneNumber
                        if (authorPhone == WppCore.getCurrentUserJid()?.phoneNumber) {
                            mConversation.runOnUiThread {
                                if (mConversation.hasWindowFocus()) {
                                    mConversation.startActivity(mConversation.intent)
                                    mConversation.overridePendingTransition(0, 0)
                                } else {
                                    mConversation.recreate()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logDebug(e)
                }
            }
        }
        return revokeBoolean
    }

    private fun showRevocationNotification(fMessage: FMessageWpp) {
        val jidAuthor = fMessage.key.remoteJid
        val isStatus = jidAuthor.isStatus
        val actualAuthor = if (isStatus) fMessage.userJid else jidAuthor
        
        val contactName = WppCore.getContactName(actualAuthor)
        val messageSuffix = Utils.getApplication().getString(if (isStatus) R.string.deleted_status else R.string.deleted_message)
        val title = if (isStatus) "Deleted Status" else "Deleted Message"
        
        val toastDeletedOption = try { prefs.getString("toastdeleted", "0")?.toIntOrNull() ?: 0 } catch (e: Exception) { 0 }
        
        if (toastDeletedOption == 1) {
            Utils.showToast("$contactName $messageSuffix", Toast.LENGTH_LONG)
        } else if (toastDeletedOption == 2) {
            val pendingIntent = createNotificationIntent(jidAuthor, isStatus)
            Utils.showNotification(title, "$contactName $messageSuffix", pendingIntent)
        }
        
        val taskerAction = if (isStatus) "deleted_status" else "deleted_message"
        Tasker.sendTaskerEvent(contactName, actualAuthor.phoneNumber ?: "", taskerAction)
    }

    private fun createNotificationIntent(jidObj: FMessageWpp.UserJid?, isStatus: Boolean): android.app.PendingIntent? {
        if (jidObj == null) return null
        try {
            val app = Utils.getApplication() ?: return null
            
            // DO NOT use ACTION_VIEW here. WhatsApp's internal activities will crash or misbehave 
            // if ACTION_VIEW is set without a data URI. We use an explicit intent instead.
            val intent = android.content.Intent()
            
            val jidString = jidObj.userRawString ?: return null
            val rawJid = if (jidString.contains("@")) jidString else "$jidString@s.whatsapp.net"
            
            if (isStatus) {
                val statusClass = XposedHelpers.findClassIfExists("com.whatsapp.status.playback.StatusPlaybackActivity", classLoader)
                if (statusClass != null) {
                    intent.setClass(app, statusClass)
                } else {
                    intent.setClassName(app.packageName, "com.whatsapp.status.playback.StatusPlaybackActivity")
                }
            } else {
                val convClass = XposedHelpers.findClassIfExists("com.whatsapp.conversation.ui.Conversation", classLoader)
                    ?: XposedHelpers.findClassIfExists("com.whatsapp.Conversation", classLoader)
                if (convClass != null) {
                    intent.setClass(app, convClass)
                } else {
                    intent.setClassName(app.packageName, "com.whatsapp.Conversation")
                }
                
                // Required extras for newer WhatsApp versions to properly initialize Conversation activity
                intent.putExtra("start_t", android.os.SystemClock.uptimeMillis())
                intent.putExtra("mat_entry_point", 64)
            }
            
            // Newer WhatsApp versions strictly require the JID to be passed as a String.
            intent.putExtra("jid", rawJid)

            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            
            var flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags = flags or android.app.PendingIntent.FLAG_IMMUTABLE
            }
            return android.app.PendingIntent.getActivity(app, rawJid.hashCode(), intent, flags)
        } catch (e: Exception) {
            XposedBridge.log("AntiRevoke: Error creating intent: $e")
            return null
        }
    }

    private fun findTextViewRecursive(view: View): TextView? {
        if (view is TextView) {
            val text = view.text.toString()
            if (text.contains(":") || text.length in 3..15 || text.lowercase().contains("now") || text.lowercase().contains("ago")) return view
            val dateId = Utils.getID("date", "id")
            if (view.id == dateId && dateId != 0) return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextViewRecursive(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    override fun getPluginName(): String = "Anti Revoke"
}