package com.wmods.wppenhacer.xposed.core.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DatabaseObserver - monitors SQLiteDatabase operations to trigger UI refreshes.
 * Used to fix broken notification counts by detecting chat table updates.
 */
object DatabaseObserver {
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val isHooked = AtomicBoolean(false)
    private val observedTables = CopyOnWriteArraySet<String>()

    interface Listener {
        fun onDatabaseChanged(table: String, operation: String)
    }

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    @JvmStatic
    fun observeTable(table: String) {
        observedTables.add(table.lowercase())
    }

    @JvmStatic
    fun init(classLoader: ClassLoader) {
        if (isHooked.compareAndSet(false, true)) {
            hookDatabase()
        }
    }

    private fun hookDatabase() {
        try {
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.hasThrowable()) return
                    val table = param.args[0] as? String ?: return

                    if (observedTables.isEmpty() || observedTables.contains(table.lowercase())) {
                        notifyListeners(table, param.method.name)
                    }
                }
            }

            // Hook common database modification methods
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "insert", String::class.java, String::class.java, ContentValues::class.java, hook)
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "insertOrThrow", String::class.java, String::class.java, ContentValues::class.java, hook)
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "insertWithOnConflict", String::class.java, String::class.java, ContentValues::class.java, Int::class.javaPrimitiveType, hook)
            
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "update", String::class.java, ContentValues::class.java, String::class.java, Array<String>::class.java, hook)
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "updateWithOnConflict", String::class.java, ContentValues::class.java, String::class.java, Array<String>::class.java, Int::class.javaPrimitiveType, hook)
            
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "delete", String::class.java, String::class.java, Array<String>::class.java, hook)
            
            XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "execSQL", String::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.hasThrowable()) return
                    val sql = param.args[0] as? String ?: return
                    
                    val sqlLower = sql.lowercase()
                    for (table in observedTables) {
                        if (sqlLower.contains(table)) {
                            notifyListeners(table, "execSQL")
                            break
                        }
                    }
                }
            })

        } catch (t: Throwable) {
            XposedBridge.log("WAE: DatabaseObserver hook failed: ${t.message}")
        }
    }

    private fun notifyListeners(table: String, operation: String) {
        for (listener in listeners) {
            runCatching { listener.onDatabaseChanged(table, operation) }
                .onFailure { XposedBridge.log(it) }
        }
    }
}
