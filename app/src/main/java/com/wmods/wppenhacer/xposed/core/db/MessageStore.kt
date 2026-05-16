package com.wmods.wppenhacer.xposed.core.db

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import androidx.core.database.sqlite.transaction
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.util.stream.Collectors

class MessageStore private constructor() {

    private var sqLiteDatabase: SQLiteDatabase? = null
    
    @Volatile
    private var favoriteJidsCache: MutableSet<String>? = null
    private var lastFavoriteRefresh: Long = 0

    init {
        initializeDatabase()
    }

    private fun initializeDatabase() {
        Utils.getExecutor().execute {
            synchronized(this) {
                if (sqLiteDatabase?.isOpen == true) return@execute
                
                try {
                    val dataDir = Utils.getApplication().filesDir.parentFile
                    val dbFile = File(dataDir, "/databases/msgstore.db")
                    
                    if (dbFile.exists()) {
                        sqLiteDatabase = SQLiteDatabase.openDatabase(
                            dbFile.absolutePath, 
                            null, 
                            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                        )
                        
                        try {
                            sqLiteDatabase?.rawQuery("PRAGMA busy_timeout = 5000", null)?.close()
                            sqLiteDatabase?.rawQuery("PRAGMA journal_mode = WAL", null)?.close()
                        } catch (ignored: Throwable) {}

                        // Trigger initial cache load
                        getFavoriteJidsSync()
                    }
                } catch (e: Exception) {
                    XposedBridge.log("MessageStore: Error initializing database: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val FAVORITE_CACHE_TIMEOUT = 30000L // 30 seconds
        
        @Volatile
        private var mInstance: MessageStore? = null

        @JvmStatic
        fun getInstance(): MessageStore {
            return mInstance ?: synchronized(this) {
                mInstance ?: MessageStore().also { mInstance = it }
            }
        }
    }

    fun getFavoriteJids(): Set<String> {
        val cache = favoriteJidsCache
        val now = System.currentTimeMillis()
        if (cache != null && (now - lastFavoriteRefresh < FAVORITE_CACHE_TIMEOUT)) {
            return HashSet(cache)
        }
        
        if (cache == null) {
            val persistent = loadFavoritesFromPrefs()
            if (persistent.isNotEmpty()) {
                favoriteJidsCache = HashSet(persistent)
                lastFavoriteRefresh = now
                return HashSet(persistent)
            }
        }
        
        Utils.getExecutor().execute { getFavoriteJidsSync() }
        return cache?.let { HashSet(it) } ?: HashSet()
    }

    @Synchronized
    fun getFavoriteJidsSync(): Set<String> {
        val now = System.currentTimeMillis()
        val cache = favoriteJidsCache
        if (cache != null && (now - lastFavoriteRefresh < FAVORITE_CACHE_TIMEOUT)) {
            return HashSet(cache)
        }

        val result = HashSet<String>()
        val db = sqLiteDatabase ?: return cache?.let { HashSet(it) } ?: result
        if (!db.isOpen) return cache?.let { HashSet(it) } ?: result

        try {
            // Standard WhatsApp favorite table
            db.rawQuery("SELECT jid.raw_string AS raw_jid FROM favorite INNER JOIN jid ON jid._id = favorite.jid_row_id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    normalizeJid(cursor.getString(0))?.let { result.add(it) }
                }
            }
        } catch (ignored: Exception) {}

        expandWithLids(result)

        if (result.isEmpty()) {
            val commonTableNames = arrayOf("favorite", "favourite", "starred_jids", "favorite_contacts", "favourite_contacts")
            for (tName in commonTableNames) {
                if (tableExists(tName)) {
                    collectFavoriteJidsFromJoin(result, tName)
                    collectFavoriteJidsFromSimpleTable(result, tName)
                }
            }
        }

        if (result.isNotEmpty()) {
            favoriteJidsCache = HashSet(result)
            lastFavoriteRefresh = System.currentTimeMillis()
            saveFavoritesToPrefs(result)
        }
        return result
    }

    private fun normalizeJid(jid: String?): String? {
        if (jid == null) return null
        val normalized = jid.trim().lowercase(java.util.Locale.US)
        if (normalized.isEmpty()) return null
        return normalized.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
    }

    private fun tableExists(tableName: String): Boolean {
        val db = sqLiteDatabase ?: return false
        return try {
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND lower(name)=lower(?) LIMIT 1", arrayOf(tableName)).use { cursor ->
                cursor.moveToFirst()
            }
        } catch (e: Exception) { false }
    }

    private fun collectFavoriteJidsFromJoin(output: MutableSet<String>, favoriteTableName: String) {
        val db = sqLiteDatabase ?: return
        val cols = getTableColumns(favoriteTableName)
        val joinCol = when {
            cols.contains("jid_row_id") -> "jid_row_id"
            cols.contains("jid_id") -> "jid_id"
            cols.contains("jid") -> "jid"
            else -> null
        }

        if (joinCol != null) {
            try {
                db.rawQuery("SELECT jid.raw_string, jid.user, jid.server FROM $favoriteTableName f JOIN jid ON f.$joinCol = jid._id", null).use { cursor ->
                    collectFavoriteJidsFromCursor(cursor, output)
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun collectFavoriteJidsFromSimpleTable(output: MutableSet<String>, tableName: String) {
        val db = sqLiteDatabase ?: return
        val cols = getTableColumns(tableName)
        val jidCol = when {
            cols.contains("jid") -> "jid"
            cols.contains("raw_string") -> "raw_string"
            cols.contains("user_jid") -> "user_jid"
            cols.contains("address") -> "address"
            else -> null
        } ?: return

        try {
            db.rawQuery("SELECT $jidCol FROM $tableName", null).use { cursor ->
                while (cursor.moveToNext()) {
                    normalizeJid(cursor.getString(0))?.let { output.add(it) }
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun collectFavoriteJidsFromCursor(cursor: Cursor?, output: MutableSet<String>) {
        if (cursor == null) return
        val rawIndex = cursor.getColumnIndex("raw_string")
        val userIndex = cursor.getColumnIndex("user")
        val serverIndex = cursor.getColumnIndex("server")
        while (cursor.moveToNext()) {
            var jid = if (rawIndex >= 0) cursor.getString(rawIndex) else null
            if (jid.isNullOrEmpty() && userIndex >= 0 && serverIndex >= 0) {
                val user = cursor.getString(userIndex)
                val server = cursor.getString(serverIndex)
                if (!user.isNullOrEmpty() && !server.isNullOrEmpty()) jid = "$user@$server"
            }
            normalizeJid(jid)?.let { output.add(it) }
        }
    }

    private fun getTableColumns(tableName: String): Set<String> {
        val columns = HashSet<String>()
        val db = sqLiteDatabase ?: return columns
        try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { columns.add(it.lowercase(java.util.Locale.US)) }
                }
            }
        } catch (ignored: Exception) {}
        return columns
    }

    private fun expandWithLids(jids: MutableSet<String>) {
        if (jids.isEmpty()) return
        try {
            val repo = com.wmods.wppenhacer.xposed.core.WppCore.getWaJidMapRepository()
            val conv = com.wmods.wppenhacer.xposed.core.WppCore.getConvertJidToLidMethod() ?: return
            
            val lids = HashSet<String>()
            for (jid in jids) {
                if (jid.endsWith("@s.whatsapp.net")) {
                    try {
                        val userJid = com.wmods.wppenhacer.xposed.core.WppCore.createUserJid(jid)
                        val lidJid = conv.invoke(repo, userJid)
                        if (lidJid != null) {
                            val lidRaw = lidJid.toString()
                            lids.add(lidRaw)
                            if (lidRaw.contains("@")) lids.add(lidRaw.split("@")[0])
                        }
                    } catch (ignored: Throwable) {}
                }
            }
            if (lids.isNotEmpty()) jids.addAll(lids)
        } catch (ignored: Throwable) {}
    }

    private fun saveFavoritesToPrefs(jids: Set<String>) {
        try {
            Utils.getApplication().getSharedPreferences("wae_favorites_cache", android.content.Context.MODE_PRIVATE)
                .edit().putStringSet("jids", jids).apply()
        } catch (ignored: Throwable) {}
    }

    private fun loadFavoritesFromPrefs(): Set<String> {
        return try {
            Utils.getApplication().getSharedPreferences("wae_favorites_cache", android.content.Context.MODE_PRIVATE)
                .getStringSet("jids", HashSet()) ?: HashSet()
        } catch (ignored: Throwable) { HashSet() }
    }

    fun getMessageById(id: Long): String {
        val db = sqLiteDatabase ?: return ""
        var message = ""
        try {
            val columns = arrayOf("c0content")
            val selection = "docid=?"
            val selectionArgs = arrayOf(id.toString())

            db.query("message_ftsv2_content", columns, selection, selectionArgs, null, null, null)
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        message = cursor.getString(cursor.getColumnIndexOrThrow("c0content"))
                    }
                }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return message
    }

    fun getCurrentMessageByKey(message_key: String): String {
        val db = sqLiteDatabase ?: return ""
        val columns = arrayOf("text_data")
        val selection = "key_id=?"
        val selectionArgs = arrayOf(message_key)
        try {
            db.query("message", columns, selection, selectionArgs, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return ""
    }

    fun getIdfromKey(message_key: String): Long {
        val db = sqLiteDatabase ?: return -1
        val columns = arrayOf("_id")
        val selection = "key_id=?"
        val selectionArgs = arrayOf(message_key)
        try {
            db.query("message", columns, selection, selectionArgs, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return -1
    }

    fun getMediaFromID(id: Long): String? {
        val db = sqLiteDatabase ?: return null
        val columns = arrayOf("file_path")
        val selection = "message_row_id=?"
        val selectionArgs = arrayOf(id.toString())
        try {
            db.query("message_media", columns, selection, selectionArgs, null, null, null)
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0)
                    }
                }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return null
    }

    fun getCurrentMessageByID(row_id: Long): String {
        val db = sqLiteDatabase ?: return ""
        val columns = arrayOf("text_data")
        val selection = "_id=?"
        val selectionArgs = arrayOf(row_id.toString())
        try {
            db.query("message", columns, selection, selectionArgs, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return ""
    }

    fun getOriginalMessageKey(id: Long): String {
        val db = sqLiteDatabase ?: return ""
        var message = ""
        val sql =
            "SELECT parent_message_row_id, key_id FROM message_add_on WHERE parent_message_row_id=\"$id\""
        try {
            db.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    message = cursor.getString(1)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return message
    }

    fun getAudioListByMessageList(messageList: List<String>?): List<String> {
        val db = sqLiteDatabase
        if (db == null || messageList.isNullOrEmpty()) {
            return ArrayList()
        }

        val list = ArrayList<String>()
        val placeholders = messageList.stream().map { "?" }.collect(Collectors.joining(","))
        val sql = "SELECT message_type FROM message WHERE key_id IN ($placeholders)"
        try {
            db.rawQuery(sql, messageList.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        if (cursor.getInt(0) == 2) {
                            list.add(cursor.getString(0))
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }

        return list
    }

    @Synchronized
    fun executeSQL(sql: String) {
        try {
            sqLiteDatabase?.execSQL(sql)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    fun storeMessageRead(messageId: String) {
        val db = sqLiteDatabase ?: return
        XposedBridge.log("storeMessageRead: $messageId")
        try {
            db.execSQL("UPDATE message SET status = 1 WHERE key_id = \"$messageId\"")
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    fun isReadMessageStatus(messageId: String): Boolean {
        val db = sqLiteDatabase ?: return false
        var result = false
        var cursor: Cursor? = null
        try {
            val columns = arrayOf("status")
            val selection = "key_id=?"
            val selectionArgs = arrayOf(messageId)

            cursor = db.query("message", columns, selection, selectionArgs, null, null, null)
            if (cursor.moveToFirst()) {
                result = cursor.getInt(cursor.getColumnIndexOrThrow("status")) == 1
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        } finally {
            cursor?.close()
        }
        return result
    }

    fun getDatabase(): SQLiteDatabase? {
        return sqLiteDatabase
    }

    @SuppressLint("Recycle")
    @Synchronized
    fun getFirstMessageInfoByChatRawJid(rawJid: String): MessageInfo? {
        val db = getDatabase()
        if (db == null || TextUtils.isEmpty(rawJid)) {
            return null
        }

        val sql = """
            WITH resolved(jid_row_id) AS (
                SELECT _id FROM jid WHERE raw_string=?
                UNION
                SELECT jm.jid_row_id FROM jid_map jm
                INNER JOIN jid j ON j._id = jm.lid_row_id
                WHERE j.raw_string=?
                UNION
                SELECT jm.lid_row_id FROM jid_map jm
                INNER JOIN jid j ON j._id = jm.jid_row_id
                WHERE j.raw_string=?
            ), chat_target AS (
                SELECT _id FROM chat WHERE jid_row_id IN (SELECT jid_row_id FROM resolved)
            )
            SELECT m._id, m.sort_id, m.chat_row_id
            FROM message m
            INNER JOIN chat_target c ON c._id = m.chat_row_id
            ORDER BY m.sort_id ASC, m._id ASC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(sql, arrayOf(rawJid, rawJid, rawJid)).use { cursor ->
                if (cursor.moveToFirst()) {
                    return MessageInfo(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return null
    }

    @Synchronized
    fun deleteStatusByMessageKey(messageKey: String?): Boolean {
        val db = sqLiteDatabase
        if (db == null || messageKey.isNullOrEmpty()) {
            return false
        }

        var messageRowId: Long? = null
        var senderJidRowId: Long? = null
        var chatRowId: Long? = null
        var mediaFilePath: String? = null
        var deleted = false

        db.transaction {
            try {
                try {
                    rawQuery(
                        "SELECT _id, sender_jid_row_id, chat_row_id " +
                                "FROM message " +
                                "WHERE key_id=? AND from_me=0 " +
                                "ORDER BY _id DESC LIMIT 1",
                        arrayOf(messageKey)
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            messageRowId = if (cursor.isNull(0)) null else cursor.getLong(0)
                            senderJidRowId = if (cursor.isNull(1)) null else cursor.getLong(1)
                            chatRowId = if (cursor.isNull(2)) null else cursor.getLong(2)
                        }
                    }
                } catch (e: Exception) {
                    XposedBridge.log(e)
                }

                if (messageRowId == null || senderJidRowId == null || chatRowId == null) {
                    return false
                }

                try {
                    rawQuery(
                        "SELECT file_path FROM message_media WHERE message_row_id=? LIMIT 1",
                        arrayOf(messageRowId.toString())
                    ).use { mediaCursor ->
                        if (mediaCursor.moveToFirst()) {
                            mediaFilePath = mediaCursor.getString(0)
                        }
                    }
                } catch (e: Exception) {
                    XposedBridge.log(e)
                }

                deleted = delete("message", "_id=?", arrayOf(messageRowId.toString())) > 0
                if (!deleted) {
                    return false
                }

                refreshStatusRow(senderJidRowId!!, chatRowId!!)
            } catch (e: Exception) {
                XposedBridge.log(e)
                deleted = false
            } finally {
            }
        }

        if (deleted) {
            deleteStatusMediaFile(mediaFilePath)
        }

        return deleted
    }

    private fun refreshStatusRow(senderJidRowId: Long, chatRowId: Long) {
        val db = sqLiteDatabase ?: return
        var latestMessageId: Long = -1
        var latestTimestamp: Long = 0
        var totalCount = 0
        var unseenCount = 0
        var firstUnreadMessageId: Long? = null

        try {
            db.rawQuery(
                "SELECT _id, timestamp, status " +
                        "FROM message " +
                        "WHERE sender_jid_row_id=? AND chat_row_id=? " +
                        "ORDER BY timestamp DESC, _id DESC",
                arrayOf(senderJidRowId.toString(), chatRowId.toString())
            ).use { cursor ->
                var first = true
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(0)
                    val ts = cursor.getLong(1)
                    val status = cursor.getInt(2)

                    if (first) {
                        latestMessageId = rowId
                        latestTimestamp = ts
                        first = false
                    }

                    totalCount++
                    if (status == 0) {
                        unseenCount++
                        firstUnreadMessageId = rowId
                    }
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }

        if (totalCount == 0) {
            db.delete("status", "jid_row_id=?", arrayOf(senderJidRowId.toString()))
            return
        }

        db.execSQL(
            "UPDATE status " +
                    "SET message_table_id=?, " +
                    "timestamp=?, " +
                    "total_count=?, " +
                    "unseen_count=?, " +
                    "unseen_count_close_friends=CASE " +
                    "WHEN unseen_count_close_friends IS NULL THEN NULL " +
                    "WHEN unseen_count_close_friends > ? THEN ? " +
                    "ELSE unseen_count_close_friends END, " +
                    "first_unread_message_table_id=? " +
                    "WHERE jid_row_id=?",
            arrayOf<Any?>(
                latestMessageId,
                latestTimestamp,
                totalCount,
                unseenCount,
                unseenCount,
                unseenCount,
                firstUnreadMessageId,
                senderJidRowId
            )
        )

        db.execSQL(
            "UPDATE status " +
                    "SET last_read_message_table_id = CASE " +
                    "WHEN last_read_message_table_id IN (" +
                    "SELECT _id FROM message WHERE sender_jid_row_id=? AND chat_row_id=?" +
                    ") THEN last_read_message_table_id ELSE NULL END, " +
                    "last_read_receipt_sent_message_table_id = CASE " +
                    "WHEN last_read_receipt_sent_message_table_id IN (" +
                    "SELECT _id FROM message WHERE sender_jid_row_id=? AND chat_row_id=?" +
                    ") THEN last_read_receipt_sent_message_table_id ELSE NULL END, " +
                    "autodownload_limit_message_table_id = CASE " +
                    "WHEN autodownload_limit_message_table_id IN (" +
                    "SELECT _id FROM message WHERE sender_jid_row_id=? AND chat_row_id=?" +
                    ") THEN autodownload_limit_message_table_id ELSE NULL END " +
                    "WHERE jid_row_id=?",
            arrayOf<Any>(
                senderJidRowId, chatRowId,
                senderJidRowId, chatRowId,
                senderJidRowId, chatRowId,
                senderJidRowId
            )
        )
    }

    private fun deleteStatusMediaFile(relativePath: String?) {
        if (relativePath.isNullOrEmpty()) {
            return
        }

        val candidates = ArrayList<File>()
        val app = Utils.getApplication()
        val appName = app.applicationInfo.loadLabel(app.packageManager).toString()
        val mediaDirs = app.externalMediaDirs

        if (relativePath.startsWith("/")) {
            candidates.add(File(relativePath))
        }

        if (mediaDirs.isNotEmpty()) {
            candidates.add(File(mediaDirs[0], "$appName/$relativePath"))
        }

        for (candidate in candidates) {
            try {
                if (candidate.exists() && candidate.isFile && candidate.delete()) {
                    break
                }
            } catch (ignored: Throwable) {
            }
        }
    }

    data class MessageInfo(val rowId: Long, val sortId: Long, val chatRowId: Long)
}