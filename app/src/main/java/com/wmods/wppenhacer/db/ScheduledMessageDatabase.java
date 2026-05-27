package com.wmods.wppenhacer.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.wmods.wppenhacer.models.ScheduledMessage;
import com.wmods.wppenhacer.models.ScheduledContact;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessageDatabase extends SQLiteOpenHelper {
    public static ScheduledMessageDatabase K0;
    public static final String q = "scheduled_messages.db";
    public static final String s = "scheduled_messages";
    public static final String x = "id";
    public static final String y = "contact_jids";
    public static final String z = "contact_names";
    public static final String A = "message";
    public static final String B = "scheduled_time";
    public static final String C = "repeat_type";
    public static final String D = "repeat_days";

    /* JADX INFO: renamed from: X, reason: collision with root package name */
    public static final String f36X = "is_active";
    public static final String Y = "is_sent";
    public static final String Z = "last_sent_time";
    public static final String F0 = "is_dispatching";
    public static final String G0 = "dispatched_time";
    public static final String H0 = "created_time";
    public static final String I0 = "whatsapp_type";
    public static final String J0 = "image_path";

    public ScheduledMessageDatabase(Context context) {
        super(context, "scheduled_messages.db", (SQLiteDatabase.CursorFactory) null, 7);
    }

    public static synchronized ScheduledMessageDatabase v(Context context) {
        try {
            if (K0 == null) {
                K0 = new ScheduledMessageDatabase(context.getApplicationContext());
            }
        } catch (Throwable th) {
            throw th;
        }
        return K0;
    }

    public long F(ScheduledMessage ScheduledMessageVar) {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("contact_jids", ScheduledMessage.z(ScheduledMessageVar.d()));
        contentValues.put("contact_names", ScheduledMessage.z(ScheduledMessageVar.f()));
        contentValues.put("message", ScheduledMessageVar.n());
        contentValues.put("scheduled_time", Long.valueOf(ScheduledMessageVar.s()));
        contentValues.put("repeat_type", Integer.valueOf(ScheduledMessageVar.r()));
        contentValues.put("repeat_days", Integer.valueOf(ScheduledMessageVar.q()));
        contentValues.put("is_active", Integer.valueOf(ScheduledMessageVar.v() ? 1 : 0));
        contentValues.put("is_sent", Integer.valueOf(ScheduledMessageVar.x() ? 1 : 0));
        contentValues.put("last_sent_time", Long.valueOf(ScheduledMessageVar.m()));
        contentValues.put("is_dispatching", Integer.valueOf(ScheduledMessageVar.w() ? 1 : 0));
        contentValues.put("dispatched_time", Long.valueOf(ScheduledMessageVar.j()));
        contentValues.put("created_time", Long.valueOf(ScheduledMessageVar.h()));
        contentValues.put("whatsapp_type", Integer.valueOf(ScheduledMessageVar.t()));
        contentValues.put("image_path", ScheduledMessageVar.l());
        long jInsert = writableDatabase.insert("scheduled_messages", null, contentValues);
        ScheduledMessageVar.B(jInsert);
        return jInsert;
    }

    public boolean J(long j) {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_dispatching", (Integer) 1);
        contentValues.put("dispatched_time", Long.valueOf(System.currentTimeMillis()));
        return writableDatabase.update("scheduled_messages", contentValues, "id = ?", new String[]{String.valueOf(j)}) > 0;
    }

    public void O(long j) {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_sent", (Integer) 1);
        contentValues.put("last_sent_time", Long.valueOf(System.currentTimeMillis()));
        contentValues.put("is_dispatching", (Integer) 0);
        contentValues.put("dispatched_time", (Integer) 0);
        writableDatabase.update("scheduled_messages", contentValues, "id = ?", new String[]{String.valueOf(j)});
    }

    public void R(long j, boolean z2) {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_active", Integer.valueOf(z2 ? 1 : 0));
        writableDatabase.update("scheduled_messages", contentValues, "id = ?", new String[]{String.valueOf(j)});
    }

    public boolean V(ScheduledMessage ScheduledMessageVar) {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("contact_jids", ScheduledMessage.z(ScheduledMessageVar.d()));
        contentValues.put("contact_names", ScheduledMessage.z(ScheduledMessageVar.f()));
        contentValues.put("message", ScheduledMessageVar.n());
        contentValues.put("scheduled_time", Long.valueOf(ScheduledMessageVar.s()));
        contentValues.put("repeat_type", Integer.valueOf(ScheduledMessageVar.r()));
        contentValues.put("repeat_days", Integer.valueOf(ScheduledMessageVar.q()));
        contentValues.put("is_active", Integer.valueOf(ScheduledMessageVar.v() ? 1 : 0));
        contentValues.put("is_sent", Integer.valueOf(ScheduledMessageVar.x() ? 1 : 0));
        contentValues.put("last_sent_time", Long.valueOf(ScheduledMessageVar.m()));
        contentValues.put("is_dispatching", Integer.valueOf(ScheduledMessageVar.w() ? 1 : 0));
        contentValues.put("dispatched_time", Long.valueOf(ScheduledMessageVar.j()));
        contentValues.put("whatsapp_type", Integer.valueOf(ScheduledMessageVar.t()));
        contentValues.put("image_path", ScheduledMessageVar.l());
        return writableDatabase.update("scheduled_messages", contentValues, "id = ?", new String[]{String.valueOf(ScheduledMessageVar.k())}) > 0;
    }

    public void b(long j) {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_dispatching", (Integer) 0);
        contentValues.put("dispatched_time", (Integer) 0);
        writableDatabase.update("scheduled_messages", contentValues, "id = ?", new String[]{String.valueOf(j)});
    }

    public void c(long j) {
        long jCurrentTimeMillis = System.currentTimeMillis() - j;
        SQLiteDatabase writableDatabase = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_dispatching", (Integer) 0);
        contentValues.put("dispatched_time", (Integer) 0);
        writableDatabase.update("scheduled_messages", contentValues, "is_dispatching = 1 AND dispatched_time > 0 AND dispatched_time <= ?", new String[]{String.valueOf(jCurrentTimeMillis)});
    }

    public final ScheduledMessage j(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex("id");
        int columnIndex2 = cursor.getColumnIndex("contact_jids");
        int columnIndex3 = cursor.getColumnIndex("contact_names");
        int columnIndex4 = cursor.getColumnIndex("message");
        int columnIndex5 = cursor.getColumnIndex("scheduled_time");
        int columnIndex6 = cursor.getColumnIndex("repeat_type");
        int columnIndex7 = cursor.getColumnIndex("repeat_days");
        int columnIndex8 = cursor.getColumnIndex("is_active");
        int columnIndex9 = cursor.getColumnIndex("is_sent");
        int columnIndex10 = cursor.getColumnIndex("last_sent_time");
        int columnIndex11 = cursor.getColumnIndex("is_dispatching");
        int columnIndex12 = cursor.getColumnIndex("dispatched_time");
        int columnIndex13 = cursor.getColumnIndex("created_time");
        int columnIndex14 = cursor.getColumnIndex("whatsapp_type");
        int columnIndex15 = cursor.getColumnIndex("image_path");
        return new ScheduledMessage(cursor.getLong(columnIndex), ScheduledMessage.y(columnIndex2 >= 0 ? cursor.getString(columnIndex2) : "[]"), ScheduledMessage.y(columnIndex3 >= 0 ? cursor.getString(columnIndex3) : "[]"), cursor.getString(columnIndex4), cursor.getLong(columnIndex5), cursor.getInt(columnIndex6), columnIndex7 >= 0 ? cursor.getInt(columnIndex7) : 0, cursor.getInt(columnIndex8) == 1, cursor.getInt(columnIndex9) == 1, cursor.getLong(columnIndex10), columnIndex11 >= 0 && cursor.getInt(columnIndex11) == 1, columnIndex12 >= 0 ? cursor.getLong(columnIndex12) : 0L, cursor.getLong(columnIndex13), columnIndex14 >= 0 ? cursor.getInt(columnIndex14) : 0, columnIndex15 >= 0 ? cursor.getString(columnIndex15) : null);
    }

    public boolean m(long j) {
        return getWritableDatabase().delete("scheduled_messages", "id = ?", new String[]{String.valueOf(j)}) > 0;
    }

    public List<ScheduledMessage> o() {
        ArrayList arrayList = new ArrayList();
        Cursor cursorQuery = getReadableDatabase().query("scheduled_messages", null, "is_active = 1", null, null, null, "scheduled_time ASC");
        if (cursorQuery.moveToFirst()) {
            do {
                arrayList.add(j(cursorQuery));
            } while (cursorQuery.moveToNext());
        }
        cursorQuery.close();
        return arrayList;
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase sQLiteDatabase) {
        sQLiteDatabase.execSQL("CREATE TABLE scheduled_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, contact_jids TEXT NOT NULL, contact_names TEXT NOT NULL, message TEXT NOT NULL, scheduled_time INTEGER NOT NULL, repeat_type INTEGER NOT NULL DEFAULT 0, repeat_days INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 1, is_sent INTEGER NOT NULL DEFAULT 0, last_sent_time INTEGER NOT NULL DEFAULT 0, is_dispatching INTEGER NOT NULL DEFAULT 0, dispatched_time INTEGER NOT NULL DEFAULT 0, created_time INTEGER NOT NULL, whatsapp_type INTEGER NOT NULL DEFAULT 0, image_path TEXT)");
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        long j;
        boolean z2;
        int i3 = 2;
        if (i < 2) {
            sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages ADD COLUMN repeat_days INTEGER NOT NULL DEFAULT 0");
        }
        int i4 = 10;
        int i5 = 9;
        int i6 = 8;
        int i7 = 4;
        int i8 = 0;
        int i9 = 3;
        int i10 = 1;
        if (i < 3) {
            sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages RENAME TO scheduled_messages_old");
            sQLiteDatabase.execSQL("CREATE TABLE scheduled_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, contact_jids TEXT NOT NULL, contact_names TEXT NOT NULL, message TEXT NOT NULL, scheduled_time INTEGER NOT NULL, repeat_type INTEGER NOT NULL DEFAULT 0, repeat_days INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 1, is_sent INTEGER NOT NULL DEFAULT 0, last_sent_time INTEGER NOT NULL DEFAULT 0, created_time INTEGER NOT NULL)");
            Cursor cursorRawQuery = sQLiteDatabase.rawQuery("SELECT id, contact_jid, contact_name, message, scheduled_time, repeat_type, repeat_days, is_active, is_sent, last_sent_time, created_time FROM scheduled_messages_old", null);
            if (cursorRawQuery.moveToFirst()) {
                while (true) {
                    long j2 = cursorRawQuery.getLong(i8);
                    j = -454805086364424551L;
                    String string = cursorRawQuery.getString(1);
                    String string2 = cursorRawQuery.getString(i3);
                    String string3 = cursorRawQuery.getString(3);
                    long j3 = cursorRawQuery.getLong(4);
                    int i11 = cursorRawQuery.getInt(5);
                    int i12 = cursorRawQuery.getInt(6);
                    int i13 = cursorRawQuery.getInt(7);
                    int i14 = cursorRawQuery.getInt(i6);
                    long j4 = cursorRawQuery.getLong(i5);
                    long j5 = cursorRawQuery.getLong(i4);
                    ArrayList arrayList = new ArrayList();
                    ArrayList arrayList2 = new ArrayList();
                    if (string != null && !string.isEmpty()) {
                        arrayList.add(string);
                    }
                    if (string2 != null && !string2.isEmpty()) {
                        arrayList2.add(string2);
                    }
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("id", Long.valueOf(j2));
                    contentValues.put("contact_jids", ScheduledMessage.z(arrayList));
                    contentValues.put("contact_names", ScheduledMessage.z(arrayList2));
                    contentValues.put("message", string3);
                    contentValues.put("scheduled_time", Long.valueOf(j3));
                    contentValues.put("repeat_type", Integer.valueOf(i11));
                    contentValues.put("repeat_days", Integer.valueOf(i12));
                    contentValues.put("is_active", Integer.valueOf(i13));
                    contentValues.put("is_sent", Integer.valueOf(i14));
                    contentValues.put("last_sent_time", Long.valueOf(j4));
                    contentValues.put("created_time", Long.valueOf(j5));
                    sQLiteDatabase.insert("scheduled_messages", null, contentValues);
                    if (!cursorRawQuery.moveToNext()) {
                        break;
                    }
                    i3 = 2;
                    i4 = 10;
                    i5 = 9;
                    i6 = 8;
                    i8 = 0;
                }
            } else {
                j = -454805086364424551L;
            }
            cursorRawQuery.close();
            sQLiteDatabase.execSQL("DROP TABLE scheduled_messages_old");
        } else {
            j = -454805086364424551L;
        }
        if (i < 4) {
            Cursor cursorRawQuery2 = sQLiteDatabase.rawQuery("PRAGMA table_info(scheduled_messages)", null);
            while (true) {
                if (!cursorRawQuery2.moveToNext()) {
                    z2 = false;
                    break;
                }
                if ("contact_jid".equals(cursorRawQuery2.getString(1))) {
                    z2 = true;
                    break;
                }
            }
            cursorRawQuery2.close();
            if (z2) {
                sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages RENAME TO scheduled_messages_old");
                sQLiteDatabase.execSQL("CREATE TABLE scheduled_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, contact_jids TEXT NOT NULL, contact_names TEXT NOT NULL, message TEXT NOT NULL, scheduled_time INTEGER NOT NULL, repeat_type INTEGER NOT NULL DEFAULT 0, repeat_days INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 1, is_sent INTEGER NOT NULL DEFAULT 0, last_sent_time INTEGER NOT NULL DEFAULT 0, created_time INTEGER NOT NULL)");
                Cursor cursorRawQuery3 = sQLiteDatabase.rawQuery("SELECT id, contact_jid, contact_name, contact_jids, contact_names, message, scheduled_time, repeat_type, repeat_days, is_active, is_sent, last_sent_time, created_time FROM scheduled_messages_old", null);
                if (cursorRawQuery3.moveToFirst()) {
                    while (true) {
                        long j6 = cursorRawQuery3.getLong(0);
                        String string4 = cursorRawQuery3.getString(i10);
                        String string5 = cursorRawQuery3.getString(2);
                        String string6 = cursorRawQuery3.getString(i9);
                        String string7 = cursorRawQuery3.getString(i7);
                        String string8 = cursorRawQuery3.getString(5);
                        long j7 = cursorRawQuery3.getLong(6);
                        int i15 = cursorRawQuery3.getInt(7);
                        int i16 = cursorRawQuery3.getInt(8);
                        int i17 = cursorRawQuery3.getInt(9);
                        int i18 = cursorRawQuery3.getInt(10);
                        long j8 = cursorRawQuery3.getLong(11);
                        long j9 = cursorRawQuery3.getLong(12);
                        if (string6 == null || string6.isEmpty() || string6.equals("[]")) {
                            ArrayList arrayList3 = new ArrayList();
                            if (string4 != null && !string4.isEmpty()) {
                                arrayList3.add(string4);
                            }
                            string6 = ScheduledMessage.z(arrayList3);
                        }
                        if (string7 == null || string7.isEmpty() || string7.equals("[]")) {
                            ArrayList arrayList4 = new ArrayList();
                            if (string5 != null && !string5.isEmpty()) {
                                arrayList4.add(string5);
                            }
                            string7 = ScheduledMessage.z(arrayList4);
                        }
                        ContentValues contentValues2 = new ContentValues();
                        contentValues2.put("id", Long.valueOf(j6));
                        contentValues2.put("contact_jids", string6);
                        contentValues2.put("contact_names", string7);
                        contentValues2.put("message", string8);
                        contentValues2.put("scheduled_time", Long.valueOf(j7));
                        contentValues2.put("repeat_type", Integer.valueOf(i15));
                        contentValues2.put("repeat_days", Integer.valueOf(i16));
                        contentValues2.put("is_active", Integer.valueOf(i17));
                        contentValues2.put("is_sent", Integer.valueOf(i18));
                        contentValues2.put("last_sent_time", Long.valueOf(j8));
                        contentValues2.put("created_time", Long.valueOf(j9));
                        sQLiteDatabase.insert("scheduled_messages", null, contentValues2);
                        if (!cursorRawQuery3.moveToNext()) {
                            break;
                        }
                        i7 = 4;
                        i9 = 3;
                        i10 = 1;
                    }
                }
                cursorRawQuery3.close();
                sQLiteDatabase.execSQL("DROP TABLE scheduled_messages_old");
            }
        }
        if (i < 5) {
            sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages ADD COLUMN whatsapp_type INTEGER NOT NULL DEFAULT 0");
        }
        if (i < 6) {
            sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages ADD COLUMN image_path TEXT");
        }
        if (i < 7) {
            sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages ADD COLUMN is_dispatching INTEGER NOT NULL DEFAULT 0");
            sQLiteDatabase.execSQL("ALTER TABLE scheduled_messages ADD COLUMN dispatched_time INTEGER NOT NULL DEFAULT 0");
        }
    }

    public List<ScheduledMessage> p() {
        ArrayList arrayList = new ArrayList();
        Cursor cursorQuery = getReadableDatabase().query("scheduled_messages", null, null, null, null, null, "scheduled_time ASC");
        if (cursorQuery.moveToFirst()) {
            do {
                arrayList.add(j(cursorQuery));
            } while (cursorQuery.moveToNext());
        }
        cursorQuery.close();
        return arrayList;
    }

    public ScheduledMessage x(long j) {
        Cursor cursorQuery = getReadableDatabase().query("scheduled_messages", null, "id = ?", new String[]{String.valueOf(j)}, null, null, null);
        ScheduledMessage ScheduledMessageVarJ = cursorQuery.moveToFirst() ? j(cursorQuery) : null;
        cursorQuery.close();
        return ScheduledMessageVarJ;
    }

    public List<ScheduledMessage> z() {
        c(120000L);
        ArrayList arrayList = new ArrayList();
        for (ScheduledMessage ScheduledMessageVar : o()) {
            if (ScheduledMessageVar.J()) {
                arrayList.add(ScheduledMessageVar);
            }
        }
        return arrayList;
    }
}
