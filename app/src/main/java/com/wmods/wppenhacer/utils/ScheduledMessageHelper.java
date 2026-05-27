package com.wmods.wppenhacer.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.ArrayList;
import com.wmods.wppenhacer.models.ScheduledMessage;
import com.wmods.wppenhacer.db.ScheduledMessageDatabase;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessageHelper {
    public static final String a = "ScheduledMessageHelper";
    public static final String b = "com.whatsapp";
    public static final String c = "com.whatsapp.w4b";
    public static final String d = "com.facebook.GET_PHONE_ID";
    public static final String e = "com.whatsapp.phoneid.PhoneIdRequestReceiver";
    public static final String f = "wae_schedule_payload";

    public static boolean b(Context context, long j) {
        ScheduledMessage ScheduledMessageVarX = ScheduledMessageDatabase.v(context).x(j);
        if (ScheduledMessageVarX == null || !ScheduledMessageVarX.v()) {
            Log.d("ScheduledMessageHelper", "Message not found or inactive: " + j);
            return false;
        }
        if (ScheduledMessageVarX.r() == 0 && ScheduledMessageVarX.x()) {
            Log.d("ScheduledMessageHelper", "One-time message already sent: " + j);
            return false;
        }
        
        ArrayList<String> jids = new ArrayList<>(ScheduledMessageVarX.d());
        if (jids.isEmpty()) {
            return false;
        }

        // Set package based on whatsapp_type
        String targetPkg = ScheduledMessageVarX.t() == 1 ? "com.whatsapp.w4b" : "com.whatsapp";
        boolean sentSuccessfully = false;
        
        for (String jid : jids) {
            Intent intent = new Intent("com.facebook.GET_PHONE_ID");
            intent.putExtra("wae_schedule_payload", true);
            intent.putExtra("number", jid);
            intent.putExtra("message", ScheduledMessageVarX.n());
            intent.putExtra("message_id", j);
            if (ScheduledMessageVarX.u()) {
                intent.putExtra("media_path", ScheduledMessageVarX.l());
            }
            
            try {
                // Wake up WhatsApp reliably by sending the broadcast directly to the statically registered receiver
                Intent intent2 = new Intent(intent);
                intent2.setComponent(new ComponentName(targetPkg, "com.whatsapp.phoneid.PhoneIdRequestReceiver"));
                context.sendBroadcast(intent2);
                Log.d("ScheduledMessageHelper", "Sent scheduled message via PhoneIdRequestReceiver to " + targetPkg + " for JID: " + jid);
                sentSuccessfully = true;
            } catch (Exception e) {
                Log.e("ScheduledMessageHelper", "Failed to send scheduled message via PhoneIdRequestReceiver to " + targetPkg, e);
            }
        }
        
        // Also fire the old intent just in case the new method fails or the app is on an old module version
        for (String jid : jids) {
            Intent intent = new Intent("com.waenhancer.MESSAGE_SENT");
            intent.putExtra("number", jid);
            intent.putExtra("message", ScheduledMessageVarX.n());
            intent.putExtra("message_id", j);
            if (ScheduledMessageVarX.u()) {
                intent.putExtra("media_path", ScheduledMessageVarX.l());
            }
            intent.setPackage(targetPkg);
            context.sendBroadcast(intent);
        }
        
        return sentSuccessfully;
    }

    public static ArrayList<String> c(Context context) {
        ArrayList<String> arrayList = new ArrayList<>();
        android.content.pm.PackageManager packageManager = context.getPackageManager();
        String[] apps = new String[]{"com.whatsapp", "com.whatsapp.w4b"};
        for (String str : apps) {
            try {
                packageManager.getPackageInfo(str, 0);
                arrayList.add(str);
            } catch (android.content.pm.PackageManager.NameNotFoundException unused) {
            }
        }
        return arrayList;
    }

    public static CharSequence d(Context context, String str) {
        return "com.whatsapp.w4b".equals(str) ? context.getText(com.wmods.wppenhacer.R.string.schedule_whatsapp_business) : context.getText(com.wmods.wppenhacer.R.string.schedule_whatsapp_normal);
    }

    public static Intent b(Context context, String str, String str2, ArrayList<String> arrayList) {
        Intent intent = new Intent();
        try {
            intent.setClassName(str, f(context, str));
        } catch (Exception e) {}
        intent.putExtra("wa_schedule_picker", true);
        intent.putExtra("picker_type", str2);
        intent.putStringArrayListExtra("contacts", arrayList == null ? new ArrayList<>() : new ArrayList<>(arrayList));
        return intent;
    }

    public static String f(Context context, String str) throws Exception {
        android.content.pm.PackageManager packageManager = context.getPackageManager();
        String[] targets = new String[]{"com.whatsapp.contact.picker.ContactPicker", "com.whatsapp.w4b.contact.picker.ContactPicker"};
        for (String next : targets) {
            try {
                packageManager.getActivityInfo(new ComponentName(str, next), 0);
                return next;
            } catch (android.content.pm.PackageManager.NameNotFoundException unused) {
            }
        }
        android.content.pm.ActivityInfo[] activityInfoArr = packageManager.getPackageInfo(str, 1).activities;
        if (activityInfoArr != null) {
            for (android.content.pm.ActivityInfo activityInfo : activityInfoArr) {
                String str2 = activityInfo.name;
                if (str2 != null && str2.endsWith("ContactPicker")) {
                    return str2;
                }
            }
        }
        throw new Exception("ContactPicker not found");
    }
}
