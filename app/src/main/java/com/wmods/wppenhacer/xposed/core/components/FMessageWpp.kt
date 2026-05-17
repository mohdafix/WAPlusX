package com.wmods.wppenhacer.xposed.core.components

import android.util.LruCache
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Date
import java.util.Objects

/**
 * @noinspection unused
 */
class FMessageWpp(fMessage: Any?) {

    companion object {
        lateinit var TYPE: Class<*>
        private var userJidMethod: Method? = null
        private var keyMessage: Field? = null
        private var getFieldIdMessage: Field? = null
        private var deviceJidField: Field? = null
        private var messageMethod: Method? = null
        private var messageWithMediaMethod: Method? = null
        private var mediaTypeField: Field? = null
        private var getOriginalMessageKey: Method? = null
        private var abstractMediaMessageClass: Class<*>? = null
        private var broadcastField: Field? = null
        private var timestampField: Field? = null

        private val VALID_DOMAINS: Set<String> = setOf(
            "s.whatsapp.net", "newsletter", "lid", "g.us", "broadcast", "status"
        )

        private val SUPPORTED_TYPES = mutableListOf<Class<*>>()
        private val baseMessageFieldCache = LruCache<Class<*>, Field>(100)

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            try {
                TYPE = Unobfuscator.loadFMessageClass(classLoader)
                SUPPORTED_TYPES.clear()
                SUPPORTED_TYPES.add(TYPE)
                
                // Add known alternatives if they exist
                try {
                    val altType = XposedHelpers.findClassIfExists("X.8kV", classLoader)
                    if (altType != null && altType != TYPE) {
                        SUPPORTED_TYPES.add(altType)
                    }
                } catch (e: Exception) {}

                UserJid.initialize(classLoader)
                userJidMethod =
                    ReflectionUtils.findMethodUsingFilter(TYPE) { method -> method.parameterCount == 0 && method.returnType == UserJid.TYPE_USERJID }
                keyMessage = Unobfuscator.loadMessageKeyField(classLoader)
                Key.TYPE = keyMessage!!.type
                messageMethod = Unobfuscator.loadNewMessageMethod(classLoader)
                messageWithMediaMethod = Unobfuscator.loadNewMessageWithMediaMethod(classLoader)
                getFieldIdMessage = Unobfuscator.loadSetEditMessageField(classLoader)
                val deviceJidClass = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.DeviceJid"
                )
                deviceJidField =
                    ReflectionUtils.findFieldUsingFilter(TYPE) { field -> field.type == deviceJidClass }
                mediaTypeField = Unobfuscator.loadMediaTypeField(classLoader)
                getOriginalMessageKey = Unobfuscator.loadOriginalMessageKey(classLoader)
                abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader)
                broadcastField = Unobfuscator.loadBroadcastTagField(classLoader)
                timestampField = Unobfuscator.loadFmessageTimestampField(classLoader)
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
        }

        @JvmStatic
        fun findBaseMessage(rawMsg: Any?): Any? {
            if (rawMsg == null) return null
            if (isFMessage(rawMsg)) return rawMsg
            
            val cls = rawMsg.javaClass
            val cachedField = baseMessageFieldCache.get(cls)
            if (cachedField != null) {
                try {
                    return cachedField.get(rawMsg)
                } catch (e: Exception) {}
            }
            
            return findFMessageRecursive(rawMsg, 0)
        }

        @JvmStatic
        fun findFMessageRecursive(obj: Any?, depth: Int): Any? {
            if (obj == null || depth > 3) return null
            if (isFMessage(obj)) return obj
            
            val cls = obj.javaClass
            val className = cls.name
            if (className.startsWith("android.") || className.startsWith("java.") || className.startsWith("kotlin.")) return null

            try {
                for (field in cls.declaredFields) {
                    if (Modifier.isStatic(field.modifiers)) continue
                    if (!field.type.isPrimitive && field.type != String::class.java) {
                        field.isAccessible = true
                        val value = field.get(obj) ?: continue
                        if (isFMessage(value)) {
                            if (depth == 0) baseMessageFieldCache.put(cls, field)
                            return value
                        }
                        // Recurse for obfuscated types
                        if (className.length < 10 || className.contains(".playback.")) {
                            val found = findFMessageRecursive(value, depth + 1)
                            if (found != null) return found
                        }
                    }
                }
            } catch (e: Exception) {}
            return null
        }

        @JvmStatic
        fun isFMessage(obj: Any?): Boolean {
            if (obj == null) return false
            return SUPPORTED_TYPES.any { it.isInstance(obj) }
        }

        @JvmStatic
        fun isFMessageClass(clazz: Class<*>): Boolean {
            return SUPPORTED_TYPES.any { it.isAssignableFrom(clazz) }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun checkUnsafeIsFMessage(classLoader: ClassLoader, clazz: Class<*>): Boolean {
            return isFMessageClass(clazz)
        }
    }

    private val fmessage: Any

    init {
        val unwrapped = Companion.findBaseMessage(fMessage) ?: throw RuntimeException("Object fMessage is null or not a FMessage container")
        this.fmessage = unwrapped
    }

    val isValid: Boolean
        get() = Companion.isFMessage(fmessage)

    val userJid: UserJid
        get() {
            return try {
                UserJid(userJidMethod?.invoke(fmessage))
            } catch (e: Exception) {
                XposedBridge.log(e)
                UserJid()
            }
        }

    val deviceJid: Any?
        get() {
            return try {
                deviceJidField?.get(fmessage)
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    val rowId: Long
        get() {
            return try {
                getFieldIdMessage?.getLong(fmessage) ?: 0L
            } catch (e: Exception) {
                XposedBridge.log(e)
                0L
            }
        }


    val key: Key by lazy {
        Key(keyMessage?.get(fmessage), this)
    }

    val originalKey: Key by lazy {
        Key(getOriginalMessageKey?.invoke(fmessage), this)
    }

    val isBroadcast: Boolean
        get() {
            return try {
                broadcastField?.getBoolean(fmessage) ?: false
            } catch (e: Exception) {
                XposedBridge.log(e)
                false
            }
        }

    fun getObject(): Any {
        return fmessage
    }

    val revocationTargetId: String?
        get() {
            try {
                val ok = originalKey
                if (ok != null && ok.messageID.isNotEmpty()) {
                    XposedBridge.log("AntiRevoke: Found target via originalKey: ${ok.messageID}")
                    return ok.messageID
                }
            } catch (e: Exception) {
                XposedBridge.log("AntiRevoke: originalKey access failed: $e")
            }
            try {
                val cls = fmessage.javaClass
                XposedBridge.log("AntiRevoke: Searching targetID via reflection in ${cls.name}...")
                for (field in cls.declaredFields) {
                    if (field.type == String::class.java && !Modifier.isStatic(field.modifiers)) {
                        field.isAccessible = true
                        val value = field.get(fmessage) as? String
                        if (value != null && value.length >= 12 && value != key.messageID) {
                            XposedBridge.log("AntiRevoke: Found potential targetID in field ${field.name}: $value")
                            return value
                        }
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log("AntiRevoke: reflection search failed: $e")
            }
            XposedBridge.log("AntiRevoke: No targetID found for revocation message")
            return null
        }

    val messageStr: String?
        get() {
            return try {
                val message = messageMethod?.invoke(fmessage) as? String
                if (message != null) return message
                messageWithMediaMethod?.invoke(fmessage) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    val timeStamp: Long
        get() {
            return try {
                timestampField?.getLong(fmessage) ?: -1L
            } catch (e: Exception) {
                XposedBridge.log(e)
                -1
            }
        }

    val time: Date?
        get() {
            return try {
                val timestamp = timestampField?.getLong(fmessage)
                if (timestamp != null && timestamp > 0) {
                    Date(timestamp)
                } else {
                    null
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    val isMediaFile: Boolean
        get() {
            return try {
                abstractMediaMessageClass?.isInstance(fmessage) ?: false
            } catch (e: Exception) {
                false
            }
        }

    val mediaFile: File?
        get() {
            try {
                if (!isMediaFile) return null
                val mediaClass = abstractMediaMessageClass ?: return null
                for (field in mediaClass.declaredFields) {
                    if (field.type.isPrimitive) continue
                    val fileField = ReflectionUtils.getFieldByType(field.type, File::class.java)
                    if (fileField != null) {
                        val mediaObject = ReflectionUtils.getObjectField(field, fmessage)
                        val mediaFile = fileField.get(mediaObject) as? File
                        if (mediaFile != null) return mediaFile
                    }
                }
                val filePath = MessageStore.getInstance().getMediaFromID(rowId) ?: return null
                if (!filePath.startsWith("file://") && !filePath.startsWith("/")) {
                    return File(WppCore.getRootWhatsAppDir(), filePath)
                }
                return File(filePath)
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
            return null
        }

    /**
     * Gets the media type of the message.
     * Media type values:
     * 2 = Voice note
     * 82 = View once voice note
     * 42 = View once image
     * 43 = View once video
     *
     * @return The media type as an integer, or -1 if an error occurs
     */
    val mediaType: Int by lazy {
        try {
            mediaTypeField?.getInt(fmessage) ?: -1
        } catch (e: Exception) {
            XposedBridge.log(e)
            -1
        }
    }

    val isViewOnce: Boolean
        get() = (mediaType == 82 || mediaType == 42 || mediaType == 43)

    val status: Int
        get() = try {
            XposedHelpers.callMethod(fmessage, "getStatus") as? Int ?: -1
        } catch (t: Throwable) {
            try {
                Unobfuscator.loadFmessageStatusMethod(fmessage.javaClass.classLoader).invoke(fmessage) as? Int ?: -1
            } catch (t2: Throwable) {
                -1
            }
        }

    /*
     * Represents the key of a WhatsApp message, containing identifiers for the message.
     */
    class Key {
        companion object {
            /**
             * The class type of the key object.
             */
            lateinit var TYPE: Class<*>
        }

        /**
         * The wrapped FMessageWpp instance associated with this key.
         */
        var fMessage: FMessageWpp? = null
            private set

        /**
         * The underlying key object from WhatsApp's code.
         */
        @JvmField
        var thisObject: Any? = null

        /**
         * The unique identifier for the message.
         */
        @JvmField
        var messageID: String

        /**
         * A boolean indicating if the message was sent by the current user.
         */
        @JvmField
        var isFromMe: Boolean = false

        /**
         * The JID of whatsapp
         */
        @JvmField
        var remoteJid: UserJid

        /**
         * Constructs a new Key instance by wrapping the original WhatsApp message key object.
         *
         * @param key The original message key object.
         */
        constructor(key: Any?) {
            this.thisObject = key
            if (key == null) {
                this.messageID = ""
                this.remoteJid = UserJid()
                return
            }
            val keyClass = key.javaClass
            val jidClass = Unobfuscator.findFirstClassUsingName(keyClass.classLoader, StringMatchType.EndsWith, "jid.Jid")
            
            // JIDs
            val jidField = ReflectionUtils.findFieldUsingFilterIfExists(keyClass) { f -> 
                jidClass.isAssignableFrom(f.type) 
            }
            this.remoteJid = UserJid(jidField?.get(key))
            
            // Message ID (String)
            val idField = ReflectionUtils.findFieldUsingFilterIfExists(keyClass) { f -> 
                f.type == String::class.java && !f.name.contains("v") // Exclude some common obfuscated strings if possible, but usually id is the first string
            }
            this.messageID = idField?.get(key) as? String ?: ""
            
            // isFromMe (Boolean)
            val fromMeField = ReflectionUtils.findFieldUsingFilterIfExists(keyClass) { f -> 
                f.type == Boolean::class.javaPrimitiveType || f.type == Boolean::class.java
            }
            this.isFromMe = fromMeField?.get(key) as? Boolean ?: false
            // XposedBridge.log("[FMessageWpp.Key] Extracted ID: $messageID, JID: ${remoteJid.phoneRawString}, RowId: ${fMessage?.rowId ?: "N/A"}")
            
            val fmessageObj = WppCore.getFMessageFromKey(key)
            if (fmessageObj != null) {
                this.fMessage = FMessageWpp(fmessageObj)
            }
        }

        constructor(key: Any?, fmessage: FMessageWpp) {
            this.thisObject = key
            this.fMessage = fmessage
            if (key == null) {
                this.messageID = ""
                this.remoteJid = UserJid()
                return
            }
            val keyClass = key.javaClass
            val jidClass = Unobfuscator.findFirstClassUsingName(keyClass.classLoader, StringMatchType.EndsWith, "jid.Jid")
            
            // JIDs
            val jidField = ReflectionUtils.findFieldUsingFilterIfExists(keyClass) { f -> 
                jidClass.isAssignableFrom(f.type) 
            }
            this.remoteJid = UserJid(jidField?.get(key))
            
            // Message ID (String)
            val idField = ReflectionUtils.findFieldUsingFilterIfExists(keyClass) { f -> 
                f.type == String::class.java
            }
            this.messageID = idField?.get(key) as? String ?: ""
            
            // isFromMe (Boolean)
            val fromMeField = ReflectionUtils.findFieldUsingFilterIfExists(keyClass) { f -> 
                f.type == Boolean::class.javaPrimitiveType || f.type == Boolean::class.java
            }
            this.isFromMe = fromMeField?.get(key) as? Boolean ?: false
            // XposedBridge.log("[FMessageWpp.Key] Extracted ID: $messageID, JID: ${remoteJid.phoneRawString}, RowId: ${fMessage?.rowId ?: "N/A"}")
        }

        constructor(messageID: String, remoteJid: UserJid, isFromMe: Boolean) {
            this.messageID = messageID
            this.isFromMe = isFromMe
            this.remoteJid = remoteJid
            var keyObj = XposedHelpers.newInstance(TYPE, remoteJid.userJid, messageID, isFromMe)
            var fmessageObj = WppCore.getFMessageFromKey(keyObj)
            if (fmessageObj != null) {
                this.thisObject = keyObj
                this.fMessage = FMessageWpp(fmessageObj)
            } else {
                keyObj = XposedHelpers.newInstance(TYPE, remoteJid.phoneJid, messageID, isFromMe)
                fmessageObj = WppCore.getFMessageFromKey(keyObj)
                if (fmessageObj != null) {
                    this.thisObject = keyObj
                    this.fMessage = FMessageWpp(fmessageObj)
                }
            }
        }

        override fun toString(): String {
            return "Key{" +
                    "thisObject=" + thisObject +
                    ", messageID='" + messageID + '\'' +
                    ", isFromMe=" + isFromMe +
                    ", remoteJid=" + remoteJid +
                    '}'
        }
    }

    override fun toString(): String {
        return "FMessageWpp{" +
                "fmessage=" + fmessage +
                " key = " + key +
                " }"
    }

    class UserJid {
        companion object {
            lateinit var TYPE_DEVICEJID: Class<*>

            lateinit var TYPE_USERJID: Class<*>

            lateinit var TYPE_JID: Class<*>

            lateinit var TYPE_PHONEUSERJID: Class<*>

            private fun checkValidLID(lid: String?): Boolean {
                return lid != null && lid.endsWith("@lid")
            }

            fun initialize(classLoader: ClassLoader) {
                TYPE_USERJID = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.UserJid"
                )
                TYPE_JID = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.Jid"
                )
                TYPE_PHONEUSERJID = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.PhoneUserJid"
                )
                TYPE_DEVICEJID = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.DeviceJid"
                )
            }

            fun forceConverter(lidOrJid: Any?): UserJid {
                val raw = try {
                    XposedHelpers.callMethod(lidOrJid, "getRawString") as? String
                } catch (ignored: Throwable) {
                    null
                }
                raw?.let {
                    val rawJidSanitized = raw.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
                    if (checkValidLID(rawJidSanitized)) {
                        return UserJid(rawJidSanitized)
                    }
                }
                return UserJid()
            }

        }

        @JvmField
        var phoneJid: Any? = null

        @JvmField
        var userJid: Any? = null


        constructor()

        constructor(rawjid: String?) {
            if (isInvalidJid(rawjid)) return
            if (checkValidLID(rawjid)) {
                this.userJid = WppCore.createUserJid(rawjid)
                this.phoneJid = WppCore.getPhoneJidFromUserJid(this.userJid)
            } else {
                this.phoneJid = WppCore.createUserJid(rawjid)
                this.userJid = WppCore.getUserJidFromPhoneJid(this.phoneJid)
            }
        }

        constructor(lidOrJid: Any?) {
            if (lidOrJid == null) return
            var raw: String? = null
            try {
                raw = XposedHelpers.callMethod(lidOrJid, "getRawString") as? String
            } catch (ignored: Throwable) {
                return
            }
            if (isInvalidJid(raw)) return
            if (checkValidLID(raw)) {
                this.userJid = lidOrJid
                this.phoneJid = WppCore.getPhoneJidFromUserJid(this.userJid)
            } else {
                this.phoneJid = lidOrJid
                this.userJid = WppCore.getUserJidFromPhoneJid(this.phoneJid)
            }
        }

        constructor(userJid: Any?, phoneJid: Any?) {
            this.userJid = userJid
            this.phoneJid = phoneJid
        }

        val phoneRawString: String? by lazy {
            if (this.phoneJid == null) return@lazy null
            val raw =
                XposedHelpers.callMethod(this.phoneJid, "getRawString") as? String
                    ?: return@lazy null
            raw.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
        }

        val userRawString: String? by lazy {
            if (this.userJid == null) return@lazy null
            val raw = try {
                XposedHelpers.callMethod(this.userJid, "getRawString") as? String
            } catch (e: Exception) {
                null
            } ?: return@lazy null
            raw.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
        }

        val phoneNumber: String?
            get() {
                val str = phoneRawString
                try {
                    if (str == null) return null
                    if (str.contains(".") && str.contains("@") && str.indexOf(".") < str.indexOf("@")) {
                        return str.substring(0, str.indexOf("."))
                    } else if (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains(
                            "@broadcast"
                        ) || str.contains("@lid")
                    ) {
                        return str.substring(0, str.indexOf("@"))
                    }
                    return str
                } catch (e: Exception) {
                    XposedBridge.log(e)
                    return str
                }
            }

        private fun isInvalidJid(rawjid: String?): Boolean {
            if (rawjid == null) return false
            val atIndex = rawjid.indexOf('@')
            if (atIndex == -1 || atIndex == rawjid.length - 1) {
                return false
            }
            val domain = rawjid.substring(atIndex + 1)
            return !VALID_DOMAINS.contains(domain)
        }

        val isStatus: Boolean
            get() {
                return Objects.equals(phoneNumber, "status")
            }

        val isNewsletter: Boolean
            get() {
                return phoneRawString?.endsWith("@newsletter") ?: false
            }

        val isBroadcast: Boolean
            get() {
                return phoneRawString?.endsWith("@broadcast") ?: false
            }

        val isGroup: Boolean
            get() {
                if (this.phoneJid == null) return false
                return phoneRawString?.endsWith("@g.us") ?: false
            }

        val isContact: Boolean
            get() {
                if (this.userJid != null) {
                    return userRawString?.endsWith("@lid") ?: false
                }
                return phoneRawString?.endsWith("@s.whatsapp.net") ?: false
            }

        val isNull: Boolean
            get() {
                return this.phoneJid == null && this.userJid == null
            }

        override fun toString(): String {
            return "UserJid{" +
                    "PhoneJid=" + phoneJid +
                    ", UserJid=" + userJid +
                    '}'
        }
    }
}