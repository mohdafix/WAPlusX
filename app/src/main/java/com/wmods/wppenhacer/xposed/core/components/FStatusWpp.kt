package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

class FStatusWpp(fstatus: Any?) {

    companion object {

        private lateinit var methodGetStatusByKey: Method

        lateinit var TYPE: Class<*>
        private lateinit var fieldFStatusKey: Field

        private var mStatusStore: Any? = null

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            FStatusKey.initialize(classLoader)
            TYPE = Unobfuscator.loadFStatusClass(classLoader)
            val fStatusKeyClass = Unobfuscator.loadFStatusKeyClass(classLoader)
            fieldFStatusKey = ReflectionUtils.getFieldByType(TYPE, fStatusKeyClass)!!
            methodGetStatusByKey = Unobfuscator.loadGetStatusByKey(classLoader)
            XposedBridge.hookAllConstructors(
                methodGetStatusByKey.declaringClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        mStatusStore = param.thisObject
                    }
                })
        }

        @JvmStatic
        fun getFStatusFromFKeyStatus(fStatusKey: FStatusKey): FStatusWpp? {
            try {
                if (mStatusStore == null) {
                    mStatusStore = methodGetStatusByKey.declaringClass.declaredConstructors.first()
                        .newInstance()
                }
                val fstatusObj = methodGetStatusByKey.invoke(mStatusStore, fStatusKey.thisObject)
                if (fstatusObj != null) {
                    return FStatusWpp(fstatusObj)
                }
            } catch (e: Exception) {
                // Ignore invocation exceptions for missing statuses
            }
            return null
        }

    }


    init {
        if (fstatus == null) throw RuntimeException("Object FStatus is null")
        if (!TYPE.isInstance(fstatus))
            throw RuntimeException("Object is not a FStatus Instance")
    }


    val fStatusKey by lazy {
        try {
            FStatusKey(fieldFStatusKey.get(fstatus))
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    val fMessage: FMessageWpp? by lazy {
        try {
            val msgObj = WppCore.getFMessageFromFStatus(fstatus)
            if (msgObj != null) {
                FMessageWpp(msgObj)
            } else {
                null
            }
        } catch (e: Exception) {
            // Ignore unsupported statuses exceptions
            null
        }
    }

    class FStatusKey {

        companion object {
            /**
             * The class type of the key object.
             */
            lateinit var TYPE: Class<*>

            @JvmStatic
            fun initialize(classLoader: ClassLoader) {
                TYPE = Unobfuscator.loadFStatusKeyClass(classLoader)
            }

        }

        @JvmField
        var senderJid: Any? = null

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
        var remoteJid: FMessageWpp.UserJid


        @JvmField
        var fStatus: FStatusWpp? = null


        val key: FMessageWpp.Key by lazy {
            try {
                ReflectionUtils.findFieldUsingFilter(TYPE) {
                    FMessageWpp.Key.TYPE.isAssignableFrom(it.type)
                }.let {
                    FMessageWpp.Key(it.get(thisObject))
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
                FMessageWpp.Key(null)
            }
        }

        constructor(key: Any?) {
            this.thisObject = key
            val jidClass = Unobfuscator.findFirstClassUsingName(TYPE.classLoader, org.luckypray.dexkit.query.enums.StringMatchType.EndsWith, "jid.Jid")
            
            // JIDs
            val jidFields = ReflectionUtils.getFieldsByType(TYPE, jidClass)
            this.remoteJid = FMessageWpp.UserJid(jidFields.getOrNull(0)?.get(key))
            this.senderJid = FMessageWpp.UserJid(jidFields.getOrNull(1)?.get(key))
            
            // Message ID (String)
            val stringFields = ReflectionUtils.getFieldsByType(TYPE, String::class.java)
            this.messageID = stringFields.getOrNull(0)?.get(key) as? String ?: ""
            
            // isFromMe (Boolean)
            val booleanFields = ReflectionUtils.getFieldsByType(TYPE, Boolean::class.javaPrimitiveType ?: Boolean::class.java)
            this.isFromMe = booleanFields.getOrNull(0)?.get(key) as? Boolean ?: false
            
            this.fStatus = getFStatusFromFKeyStatus(this)
        }

        override fun toString(): String {
            return "FStatusKey{" +
                    "thisObject=" + thisObject +
                    ", messageID='" + messageID + '\'' +
                    ", isFromMe=" + isFromMe +
                    ", remoteJid=" + remoteJid +
                    ", senderJid=" + senderJid +
                    '}'
        }
    }

}