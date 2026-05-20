package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.BaseAdapter
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.db.DatabaseObserver
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.ResId
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.utils.BottomNavigationConfig
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.*
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * SeparateGroup - Unified Tab Manager.
 * Supports: Separate Groups, Separate Favorites, Reordering, and Hiding tabs.
 */
class SeparateGroup(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences), DatabaseObserver.Listener {

    companion object {
        const val TAB_CHATS = BottomNavigationConfig.TAB_CHATS
        const val TAB_STATUS = BottomNavigationConfig.TAB_UPDATES
        const val TAB_GROUPS = BottomNavigationConfig.TAB_GROUPS
        const val TAB_FAVORITES = BottomNavigationConfig.TAB_FAVORITES

        @JvmField
        var tabs = ArrayList<Int>()
        @JvmField
        var fragmentMap = HashMap<Int, Any>()
        @JvmField
        var instance: SeparateGroup? = null
        @JvmField
        var pagerInstance: Any? = null
        
        private val RESTORED_TAB_PATTERN = Pattern.compile("(?:android:switcher:\\d+:|conversations-list-)(\\d+)")
    }

    init {
        instance = this
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val jidCache = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private var jidBaseClass: Class<*>? = null
    private var bottomNavigationViewCls: Class<*>? = null
    private var conversationsFragmentClass: Class<*>? = null
    private val favoriteJids = HashSet<String>()
    private var lastFavoriteRefresh: Long = 0
    
    // For badge updates
    private var bottomNavViewInstance: Any? = null
    private var enableCountTabMethod: Method? = null
    private var badgeWrapperCtor: Constructor<*>? = null
    private var badgeItemCtor: Constructor<*>? = null
    private var emptyBadgeItem: Any? = null
    private var refreshRunnable: Runnable? = null

    override fun doHook() {
        if (!isAnyTabFeatureEnabled()) return

        initJidClasses()
        refreshFavorites()
        initBadgeMembers()

        bottomNavigationViewCls = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            ".BottomNavigationView"
        )
        XposedHelpers.findAndHookMethod(
            bottomNavigationViewCls,
            "getMaxItemCount",
            XC_MethodReplacement.returnConstant(99)
        )

        hookTabList()
        hookTabIcon()
        hookTabInstance()
        hookTabName()
        hookTabCounts()
        hookFragmentLifecycle()
        hookPagerInstance()
        
        // Register for database changes to update badges in real-time
        DatabaseObserver.observeTable("chat")
        DatabaseObserver.addListener(this)
    }

    private fun isAnyTabFeatureEnabled(): Boolean {
        return prefs.getBoolean("separategroups", false) || 
               prefs.getBoolean("separatefavorites", false) ||
               prefs.getBoolean("igstatus", false) ||
               prefs.getBoolean("native_igstatus", false) ||
               (prefs.getStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, null)?.isNotEmpty() ?: false) ||
               (prefs.getString(BottomNavigationConfig.PREF_TAB_ORDER, null)?.isNotEmpty() ?: false)
    }

    private fun groupsEnabled() = prefs.getBoolean("separategroups", false)
    private fun favoritesEnabled() = prefs.getBoolean("separatefavorites", false)

    private fun isManagedTab(tabId: Int): Boolean {
        return tabId == TAB_CHATS || 
               (tabId == TAB_GROUPS && groupsEnabled()) || 
               (tabId == TAB_FAVORITES && favoritesEnabled())
    }

    private fun isCustomTab(tabId: Int): Boolean {
        return (tabId == TAB_GROUPS && groupsEnabled()) || (tabId == TAB_FAVORITES && favoritesEnabled())
    }

    private fun initBadgeMembers() {
        try {
            enableCountTabMethod = Unobfuscator.loadEnableCountTabMethod(classLoader)
            badgeWrapperCtor = Unobfuscator.loadEnableCountTabBadgeWrapper(classLoader).apply { isAccessible = true }
            badgeItemCtor = Unobfuscator.loadEnableCountTabBadgeItem(classLoader).apply { isAccessible = true }
            val emptyBadgeClass = Unobfuscator.loadEnableCountTabEmptyBadgeClass(classLoader)
            
            emptyBadgeItem = try {
                XposedHelpers.getStaticObjectField(emptyBadgeClass, "INSTANCE")
                    ?: XposedHelpers.getStaticObjectField(emptyBadgeClass, "A00")
                    ?: emptyBadgeClass.constructors.firstOrNull()?.apply { isAccessible = true }?.newInstance()
            } catch (e: Throwable) { null }
        } catch (ignored: Throwable) {}
    }

    private fun hookPagerInstance() {
        try {
            val homeActivityClass = WppCore.getHomeActivityClass(classLoader)
            XposedHelpers.findAndHookMethod(homeActivityClass, "onCreate", Bundle::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val tabsPagerClass = WppCore.getTabsPagerClass(classLoader)
                    val field = ReflectionUtils.getFieldByType(param.thisObject.javaClass, tabsPagerClass)
                    pagerInstance = field?.get(param.thisObject)
                }
            })
        } catch (ignored: Throwable) {}
    }

    private fun initJidClasses() {
        try {
            jidBaseClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid")
        } catch (ignored: Throwable) {}
    }

    override fun getPluginName(): String = "Separate Group"

    private fun hookTabList() {
        val onCreateTabList = Unobfuscator.loadTabListMethod(classLoader)
        XposedBridge.hookMethod(onCreateTabList, object : XC_MethodHook() {
            @Suppress("UNCHECKED_CAST")
            override fun afterHookedMethod(param: MethodHookParam) {
                val resultTabs = param.result as? ArrayList<Int> ?: return
                tabs = resultTabs
                if (tabs.isEmpty()) return

                // Add custom tabs if they are not already there
                if (groupsEnabled() && !tabs.contains(TAB_GROUPS)) {
                    tabs.add(TAB_GROUPS)
                }
                if (favoritesEnabled() && !tabs.contains(TAB_FAVORITES)) {
                    tabs.add(TAB_FAVORITES)
                }

                prefs.reload()
                val savedOrderStr = prefs.getString(BottomNavigationConfig.PREF_TAB_ORDER, "") ?: ""
                val hiddenTabsSet = prefs.getStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, HashSet<String>()) ?: HashSet<String>()
                val forceStatusVisible = prefs.getBoolean("igstatus", false) || prefs.getBoolean("native_igstatus", false)

                BottomNavigationConfig.applyTabsConfiguration(tabs, savedOrderStr, hiddenTabsSet, forceStatusVisible)
            }
        })
    }

    private fun hookTabIcon() {
        val menuAddAndroidX = Unobfuscator.loadAddMenuAndroidX(classLoader)
        XposedBridge.hookMethod(menuAddAndroidX, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args.size <= 2 || param.args[1] !is Int) return
                val tabId = param.args[1] as Int
                if (!isCustomTab(tabId) || param.result !is MenuItem) return

                val menuItem = param.result as MenuItem
                if (tabId == TAB_GROUPS) {
                    var icon = Utils.getID("home_tab_communities_selector", "drawable")
                    if (icon == 0) icon = Utils.getID("ic_community", "drawable")
                    if (icon == 0) icon = Utils.getID("vec_ic_community", "drawable")
                    if (icon == 0) icon = android.R.drawable.ic_menu_myplaces
                    menuItem.setIcon(icon)
                } else if (tabId == TAB_FAVORITES) {
                    var icon = Utils.getID("ic_star", "drawable")
                    if (icon == 0) icon = Utils.getID("star", "drawable")
                    if (icon == 0) icon = android.R.drawable.btn_star_big_on
                    menuItem.setIcon(icon)
                }
            }
        })
    }

    private fun hookTabName() {
        val tabNameMethod = Unobfuscator.loadTabNameMethod(classLoader)
        XposedBridge.hookMethod(tabNameMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tab = param.args[0] as Int
                if (tab == TAB_GROUPS && groupsEnabled()) {
                    param.result = getStringOrFallback("groups", "Groups")
                } else if (tab == TAB_FAVORITES && favoritesEnabled()) {
                    param.result = getStringOrFallback("favorites", "Favorites")
                }
            }
        })
    }

    private fun getStringOrFallback(key: String, fallback: String): String {
        try {
            val cached = UnobfuscatorCache.getInstance()?.getString(key)
            if (!cached.isNullOrEmpty()) return cached
            
            val app = Utils.getApplication()
            val id = when (key) {
                "groups" -> ResId.string.groups
                "favorites" -> ResId.string.favorites
                else -> Utils.getID(key, "string")
            }
            if (id != 0) return app.getString(id)
        } catch (ignored: Throwable) {}
        return fallback
    }

    private fun hookTabInstance() {
        conversationsFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".ConversationsFragment"
        )

        val getTabMethod = Unobfuscator.loadGetTabMethod(classLoader)
        val tabFragmentMethod = Unobfuscator.loadTabFragmentMethod(classLoader)
        val recreateFragmentCtor = Unobfuscator.loadRecreateFragmentConstructor(classLoader)
        val fragmentClass = Unobfuscator.loadFragmentClass(classLoader)

        XposedBridge.hookMethod(recreateFragmentCtor, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val state = getRestoredStateString(param) ?: return
                val matcher = RESTORED_TAB_PATTERN.matcher(state)
                if (!matcher.find()) return

                val tabIdValue = matcher.group(1)?.toIntOrNull() ?: return
                val tabId = BottomNavigationConfig.normalizeTabId(tabIdValue)
                if (!isManagedTab(tabId)) return

                val fragmentField = ReflectionUtils.getFieldByExtendType(param.thisObject.javaClass, fragmentClass)
                val fragment = fragmentField?.get(param.thisObject)
                if (fragment != null) fragmentMap[tabId] = fragment
            }
        })

        XposedBridge.hookMethod(getTabMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val index = param.args[0] as Int
                if (index < 0 || index >= tabs.size) return
                val tabId = tabs[index]
                if (!isManagedTab(tabId)) return

                val fragment = conversationsFragmentClass?.newInstance() ?: return
                XposedHelpers.setAdditionalInstanceField(fragment, "tabId", tabId)
                param.result = fragment
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val index = param.args[0] as Int
                if (index < 0 || index >= tabs.size) return
                val tabId = tabs[index]
                if (tabId > 0 && param.result != null) {
                    fragmentMap[tabId] = param.result
                }
            }
        })

        XposedBridge.hookMethod(tabFragmentMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val chatsList = param.result as? List<*> ?: return
                param.result = filterChat(param.thisObject, chatsList)
            }
        })

        val fabMethod = Unobfuscator.loadFabMethod(classLoader)
        XposedBridge.hookMethod(fabMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                for ((tabId, fragment) in fragmentMap) {
                    if (tabId != TAB_CHATS && fragment == param.thisObject) {
                        param.result = tabId
                        return
                    }
                }
            }
        })

        hookFilterResults(tabFragmentMethod.declaringClass)
    }

    private fun hookFragmentLifecycle() {
        val cls = conversationsFragmentClass ?: return
        try {
            XposedBridge.hookAllMethods(cls, "onDestroy", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    fragmentMap.values.removeIf { it == param.thisObject }
                }
            })
        } catch (ignored: Throwable) {}
    }

    private fun hookFilterResults(declaringClass: Class<*>) {
        try {
            val publishResultsMethod = Unobfuscator.loadGetFiltersMethod(classLoader)
            XposedBridge.hookMethod(publishResultsMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val filters = param.args[1] ?: return
                    val values = XposedHelpers.getObjectField(filters, "values") as? List<*> ?: return

                    var baseField = ReflectionUtils.getFieldByExtendType(publishResultsMethod.declaringClass, BaseAdapter::class.java)
                    if (baseField == null) {
                        try {
                            val rvAdapterClass = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView\$Adapter")
                            baseField = ReflectionUtils.getFieldByExtendType(publishResultsMethod.declaringClass, rvAdapterClass)
                        } catch (ignored: Throwable) {}
                    }
                    if (baseField == null) return
                    
                    val convField = ReflectionUtils.getFieldByType(baseField.type, conversationsFragmentClass) ?: return
                    val fragment = convField.get(baseField.get(param.thisObject)) ?: return

                    val result = filterChat(fragment, values)
                    XposedHelpers.setObjectField(filters, "values", result)
                    XposedHelpers.setIntField(filters, "count", result.size)
                }
            })
        } catch (ignored: Throwable) {}
    }

    private fun getRestoredStateString(param: XC_MethodHook.MethodHookParam): String? {
        return try {
            val arg0 = param.args[0]
            if (arg0 is Bundle) {
                arg0.getParcelable<android.os.Parcelable>("state")?.toString()
            } else if (param.args.size >= 3) {
                param.args[2]?.toString()
            } else null
        } catch (e: Throwable) { null }
    }

    private fun filterChat(fragment: Any, chatsList: List<*>): List<*> {
        val tabId = findTabForFragment(fragment)
        if (!isManagedTab(tabId)) return chatsList
        if (tabId == TAB_FAVORITES) refreshFavorites()
        
        val filtered = ConversationFilter(tabId)
        @Suppress("UNCHECKED_CAST")
        filtered.addAll(chatsList as Collection<Any>)
        return filtered
    }

    private fun findTabForFragment(fragment: Any): Int {
        val additional = XposedHelpers.getAdditionalInstanceField(fragment, "tabId")
        if (additional is Int) return additional
        for ((tabId, frag) in fragmentMap) {
            if (frag == fragment) return tabId
        }
        return -1
    }

    private fun refreshFavorites() {
        if (!favoritesEnabled()) return
        val now = System.currentTimeMillis()
        if (now - lastFavoriteRefresh < 3000) return
        
        synchronized(favoriteJids) {
            if (now - lastFavoriteRefresh < 3000) return
            favoriteJids.clear()
            MessageStore.getInstance().getFavoriteJids().forEach { jid ->
                normalizeJid(jid)?.let { favoriteJids.add(it) }
            }
            lastFavoriteRefresh = now
        }
    }

    private fun normalizeJid(jid: String?): String? {
        if (jid.isNullOrBlank()) return null
        val normalized = jid.trim().lowercase(Locale.US)
        return normalized.replaceFirst(Regex("\\.[\\d:]+@"), "@")
    }

    private fun hookTabCounts() {
        if (enableCountTabMethod == null) return
        
        XposedBridge.hookMethod(enableCountTabMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tabIndex = param.args[2] as Int
                if (tabs.isEmpty() || tabIndex < 0 || tabIndex >= tabs.size) return

                val tabId = tabs[tabIndex]
                if (isManagedTab(tabId)) {
                    param.result = null // Block native badge logic
                    bottomNavViewInstance = param.thisObject
                    refreshBadges(param.args[0])
                }
            }
        })
    }

    private fun refreshBadges(menuObj: Any? = null) {
        scope.launch {
            val counts = calculateTabCounts()
            val menu = menuObj ?: try {
                XposedHelpers.getObjectField(bottomNavViewInstance, "A0G") 
                    ?: XposedHelpers.getObjectField(bottomNavViewInstance, "A02")
            } catch (e: Throwable) { null }
            
            if (menu != null) {
                applyBadges(bottomNavViewInstance!!, menu, counts)
            }
        }
    }

    private suspend fun calculateTabCounts(): Map<Int, Int> = withContext(Dispatchers.IO) {
        val counts = mutableMapOf(TAB_CHATS to 0, TAB_GROUPS to 0, TAB_FAVORITES to 0)
        try {
            val db = MessageStore.getInstance().getDatabase()
            if (db == null || !db.isOpen) return@withContext counts
            
            val hasGroups = groupsEnabled()
            val hasFavs = favoritesEnabled()
            val favJids = if (hasFavs) MessageStore.getInstance().getFavoriteJids() else emptySet()

            val sql = "SELECT chat.group_type, chat.archived, jid.server, jid.user " +
                      "FROM chat LEFT JOIN jid ON jid._id = chat.jid_row_id " +
                      "WHERE chat.unseen_message_count != 0"

            db.rawQuery(sql, null).use { cursor ->
                val colGroupType = cursor.getColumnIndex("group_type")
                val colArchived = cursor.getColumnIndex("archived")
                val colServer = cursor.getColumnIndex("server")
                val colUser = cursor.getColumnIndex("user")

                while (cursor.moveToNext()) {
                    if (colArchived >= 0 && cursor.getInt(colArchived) != 0) continue
                    
                    val groupType = if (colGroupType >= 0) cursor.getInt(colGroupType) else 0
                    if (groupType != 0 && groupType != 1 && groupType != 6 && groupType != 7) continue

                    val server = if (colServer >= 0) cursor.getString(colServer) else null
                    val user = if (colUser >= 0) cursor.getString(colUser) else null
                    val jid = if (user != null && server != null) "$user@$server".lowercase(Locale.US) else null

                    val isGroup = (server != null && (server.contains("g.us") || server.contains("broadcast"))) || (groupType != 0)
                    
                    var isFavorite = false
                    if (hasFavs && jid != null) {
                        val normalized = jid.replaceFirst(Regex("\\.[\\d:]+@"), "@")
                        isFavorite = favJids.contains(normalized) || favJids.contains(jid)
                    }

                    when {
                        isFavorite && hasFavs -> counts[TAB_FAVORITES] = counts[TAB_FAVORITES]!! + 1
                        isGroup && hasGroups -> counts[TAB_GROUPS] = counts[TAB_GROUPS]!! + 1
                        else -> counts[TAB_CHATS] = counts[TAB_CHATS]!! + 1
                    }
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("WAE: calculateTabCounts error: ${e.message}")
        }
        counts
    }

    private fun applyBadges(thisObj: Any, menuObj: Any, counts: Map<Int, Int>) {
        if (tabs.isEmpty() || enableCountTabMethod == null || badgeWrapperCtor == null || badgeItemCtor == null) return

        val managedIds = intArrayOf(TAB_CHATS, TAB_GROUPS, TAB_FAVORITES)
        for (id in managedIds) {
            try {
                val idx = tabs.indexOf(id)
                if (idx < 0) continue

                val count = counts[id] ?: 0
                val badge = if (count <= 0) {
                    badgeWrapperCtor!!.newInstance(emptyBadgeItem)
                } else {
                    badgeWrapperCtor!!.newInstance(badgeItemCtor!!.newInstance(count))
                }
                
                XposedBridge.invokeOriginalMethod(enableCountTabMethod, thisObj, arrayOf(menuObj, badge, idx))
            } catch (ignored: Throwable) {}
        }
    }

    override fun onDatabaseChanged(table: String, operation: String) {
        if (table.lowercase() == "chat" && bottomNavViewInstance != null) {
            val handler = Handler(Looper.getMainLooper())
            refreshRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                refreshBadges()
            }
            refreshRunnable = runnable
            handler.postDelayed(runnable, 500)
        }
    }

    private inner class ConversationFilter(private val tabId: Int) : ArrayList<Any>() {
        override fun add(index: Int, element: Any) {
            if (shouldKeep(element)) super.add(index, element)
        }

        override fun add(element: Any): Boolean {
            return if (shouldKeep(element)) super.add(element) else true
        }

        override fun addAll(elements: Collection<Any>): Boolean {
            elements.forEach { if (shouldKeep(it)) super.add(it) }
            return true
        }

        private fun shouldKeep(chat: Any): Boolean {
            val jid = getChatJid(chat) ?: return true
            val isGroup = jid.endsWith("@g.us") || jid.endsWith("@broadcast")
            
            val normalized = normalizeJid(jid)
            var isFavorite = false
            if (normalized != null) {
                isFavorite = favoriteJids.contains(normalized)
                if (!isFavorite) {
                    val local = if (normalized.contains("@")) normalized.split("@")[0] else normalized
                    for (fav in favoriteJids) {
                        if (fav.startsWith("$local@")) { isFavorite = true; break }
                    }
                }
            }

            return when (tabId) {
                TAB_GROUPS -> isGroup
                TAB_FAVORITES -> isFavorite
                else -> (!groupsEnabled() || !isGroup) && (!favoritesEnabled() || !isFavorite)
            }
        }

        private fun getChatJid(chat: Any): String? {
            jidCache[chat]?.let { return it }
            
            var resolved: String? = null
            try {
                for (f in chat.javaClass.declaredFields) {
                    if (jidBaseClass != null && jidBaseClass!!.isAssignableFrom(f.type)) {
                        f.isAccessible = true
                        resolved = extractJidStr(f.get(chat))
                        if (resolved != null) break
                    }
                }
                
                if (resolved == null) {
                    arrayOf("A00", "A01", "A02", "A03", "A04").forEach { name ->
                        try {
                            val jidObj = XposedHelpers.getObjectField(chat, name)
                            resolved = extractJidStr(jidObj)
                            if (resolved != null) return@forEach
                        } catch (ignored: Throwable) {}
                    }
                }
            } catch (ignored: Throwable) {}

            if (resolved != null) jidCache[chat] = resolved
            return resolved
        }

        private var jidGetRawStringMethod: Method? = null
        private fun extractJidStr(jidObj: Any?): String? {
            if (jidObj == null) return null
            try {
                if (jidGetRawStringMethod == null) {
                    jidGetRawStringMethod = Unobfuscator.loadJidGetRawStringMethod(classLoader)
                }
                if (jidGetRawStringMethod != null && jidGetRawStringMethod!!.declaringClass.isAssignableFrom(jidObj.javaClass)) {
                    return jidGetRawStringMethod!!.invoke(jidObj) as? String
                }
            } catch (ignored: Throwable) {}
            
            val str = jidObj.toString()
            return if (str.contains("@") || str.contains("-")) str else null
        }
    }
}