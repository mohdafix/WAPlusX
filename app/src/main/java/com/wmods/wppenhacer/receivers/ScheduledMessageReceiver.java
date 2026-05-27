package com.wmods.wppenhacer.receivers;

import com.wmods.wppenhacer.models.ScheduledMessage;
import com.wmods.wppenhacer.db.ScheduledMessageDatabase;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.wmods.wppenhacer.services.ScheduledMessageService;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessageReceiver extends BroadcastReceiver {
    public static final String a = "com.wmods.wppenhacer.SEND_MESSAGE";
    public static final String b = "com.wmods.wppenhacer.MESSAGE_SENT";
    public static final String c = "ScheduledMessageReceiver";

    public final void a(Context context, Intent intent) {
        String strB;
        long longExtra = intent.getLongExtra("message_id", -1L);
        boolean booleanExtra = intent.getBooleanExtra("success", false);
        String stringExtra = intent.getStringExtra("error_reason");
        String strB2 = "ScheduledMessageReceiver";
        StringBuilder sb = new StringBuilder();
        sb.append("Message sent callback - ID: ");
        sb.append(longExtra);
        sb.append(", Success: ");
        sb.append(booleanExtra);
        if (stringExtra != null) {
            strB = ", reason=" + stringExtra;
        } else {
            strB = "";
        }
        sb.append(strB);
        Log.d(strB2, sb.toString());
        if (longExtra == -1) {
            return;
        }
        ScheduledMessageDatabase ScheduledMessageDatabaseVarV = ScheduledMessageDatabase.v(context);
        if (!booleanExtra) {
            ScheduledMessageDatabaseVarV.b(longExtra);
            Log.d("ScheduledMessageReceiver", "Cleared dispatching state for message " + longExtra);
            return;
        }
        ScheduledMessageDatabaseVarV.O(longExtra);
        ScheduledMessage ScheduledMessageVarX = ScheduledMessageDatabaseVarV.x(longExtra);
        if (ScheduledMessageVarX != null && ScheduledMessageVarX.r() == 0) {
            ScheduledMessageDatabaseVarV.R(longExtra, false);
        }
        Log.d("ScheduledMessageReceiver", "Marked message " + longExtra + " as sent");
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("ScheduledMessageReceiver", "Received action: " + action);
        if (!"android.intent.action.BOOT_COMPLETED".equals(action) && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            if ("com.wmods.wppenhacer.MESSAGE_SENT".equals(action)) {
                a(context, intent);
            }
        } else if (ScheduledMessageService.m(context)) {
            ScheduledMessageService.n(context);
            Log.d("ScheduledMessageReceiver", "Started service from boot");
        }
    }
}
