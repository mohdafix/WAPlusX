package com.wmods.wppenhacer.services;

import com.wmods.wppenhacer.models.ScheduledMessage;
import com.wmods.wppenhacer.utils.ScheduledMessageHelper;
import com.wmods.wppenhacer.db.ScheduledMessageDatabase;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.MainActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessageService extends Service {
    public ScheduledMessageDatabase q;
    public boolean s = false;
    public boolean x = false;
    public final BroadcastReceiver y = new a();
    public static final String z = "com.wmods.wppenhacer.SEND_SCHEDULED_MESSAGE";
    public static final String A = "com.wmods.wppenhacer.CHECK_SCHEDULED_MESSAGES";
    public static final String B = "com.wmods.wppenhacer.MESSAGE_SENT";
    public static final String C = "com.wmods.wppenhacer.SCHEDULED_MESSAGES_STATUS";
    public static final String D = "message_id";

    /* JADX INFO: renamed from: X, reason: collision with root package name */
    public static final String f125X = "has_pending";
    public static final String Y = "pending_count";
    public static final String Z = "needs_whatsapp";
    public static final String F0 = "needs_business";
    public static final String G0 = "ScheduledMessageService";
    public static final String H0 = "scheduled_messages_channel";

    /* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
    public class a extends BroadcastReceiver {
        public a() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("com.wmods.wppenhacer.MESSAGE_SENT".equals(intent.getAction())) {
                ScheduledMessageService.this.g(intent);
            }
        }
    }

    public static boolean m(Context context) {
        return !ScheduledMessageDatabase.v(context).o().isEmpty();
    }

    public static void n(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") != 0) {
            Log.w("ScheduledMessageService", "Notification permission not granted, service may not show notifications");
        }
        Intent intent = new Intent(context, (Class<?>) ScheduledMessageService.class);
        intent.setAction("com.wmods.wppenhacer.CHECK_SCHEDULED_MESSAGES");
        try {
            context.startForegroundService(intent);
            Log.d("ScheduledMessageService", "Service start requested");
        } catch (Exception e) {
            Log.e("ScheduledMessageService", "Failed to start service", e);
        }
    }

    public final void b() {
        AlarmManager alarmManager = (AlarmManager) getSystemService("alarm");
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(this, (Class<?>) ScheduledMessageService.class);
        intent.setAction("com.wmods.wppenhacer.CHECK_SCHEDULED_MESSAGES");
        PendingIntent service = PendingIntent.getService(this, 0, intent, 201326592);
        alarmManager.cancel(service);
        service.cancel();
        Log.d("ScheduledMessageService", "Cancelled next scheduled check");
    }

    public final void c() {
        this.q.c(120000L);
        List<ScheduledMessage> listO = this.q.o();
        long j = -454858777750591847L;
        Log.d("ScheduledMessageService", "Checking messages. Active count: " + listO.size());
        ScheduledMessage ScheduledMessageVar = null;
        if (listO.isEmpty()) {
            Log.d("ScheduledMessageService", "No active messages, updating notification");
            p(0, null);
            k();
            return;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        long j2 = Long.MAX_VALUE;
        for (ScheduledMessage ScheduledMessageVar2 : listO) {
            long jP = ScheduledMessageVar2.p();
            String strB = "ScheduledMessageService";
            StringBuilder sb = new StringBuilder();
            sb.append("Message ");
            long j3 = j;
            sb.append(ScheduledMessageVar2.k());
            sb.append(" (");
            sb.append(ScheduledMessageVar2.e());
            sb.append("): nextTime=");
            sb.append(f(jP));
            sb.append(", now=");
            sb.append(f(jCurrentTimeMillis));
            sb.append(", diff=");
            sb.append((jP - jCurrentTimeMillis) / 1000);
            sb.append("s, shouldSend=");
            sb.append(ScheduledMessageVar2.J());
            Log.d(strB, sb.toString());
            if (jP < j2 && !ScheduledMessageVar2.J()) {
                ScheduledMessageVar = ScheduledMessageVar2;
                j2 = jP;
            }
            j = j3;
        }
        long j4 = j;
        List<ScheduledMessage> listZ = this.q.z();
        if (listZ.isEmpty()) {
            Log.d("ScheduledMessageService", "No pending messages ready to send now");
            p(listO.size(), ScheduledMessageVar);
            k();
            return;
        }
        Log.d("ScheduledMessageService", "Found " + listZ.size() + " pending messages ready to send");
        ArrayList arrayList = new ArrayList();
        Iterator<ScheduledMessage> it = listZ.iterator();
        while (it.hasNext()) {
            arrayList.add(Long.valueOf(it.next().k()));
        }
        Iterator it2 = arrayList.iterator();
        while (it2.hasNext()) {
            j(((Long) it2.next()).longValue());
        }
        q();
        k();
    }

    public final Notification d(int i) {
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.setFlags(335544320);
        return new NotificationCompat.Builder(this, H0).setContentTitle(getString(R.string.scheduled_messages)).setContentText(i > 0 ? getString(R.string.scheduled_messages_active, Integer.valueOf(i)) : getString(R.string.scheduled_messages_monitoring)).setSmallIcon(R.drawable.ic_schedule).setPriority(-1).setOngoing(true).setContentIntent(PendingIntent.getActivity(this, 0, intent, 67108864)).setCategory("service").setVisibility(1).build();
    }

    public final void e() {
        NotificationChannel notificationChannel = new NotificationChannel("scheduled_messages_channel", getString(R.string.scheduled_messages), 2);
        notificationChannel.setDescription(getString(R.string.scheduled_messages_desc));
        notificationChannel.setShowBadge(true);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(notificationChannel);
            Log.d("ScheduledMessageService", "Notification channel created");
        }
    }

    public final String f(long j) {
        return new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date(j));
    }

    public final void g(Intent intent) {
        long longExtra = intent.getLongExtra("message_id", -1L);
        if (longExtra == -1) {
            Log.w("ScheduledMessageService", "Ignoring callback without message ID");
            return;
        }
        boolean booleanExtra = intent.getBooleanExtra("success", false);
        String stringExtra = intent.getStringExtra("error_reason");
        ScheduledMessage ScheduledMessageVarX = this.q.x(longExtra);
        if (booleanExtra) {
            this.q.O(longExtra);
            if (ScheduledMessageVarX != null && ScheduledMessageVarX.r() == 0) {
                this.q.R(longExtra, false);
            }
            Log.d("ScheduledMessageService", "WhatsApp confirmed sent message ID: " + longExtra);
        } else {
            this.q.b(longExtra);
            if (stringExtra == null || stringExtra.isEmpty()) {
                Log.w("ScheduledMessageService", "WhatsApp send failed for message ID: " + longExtra);
            } else {
                Log.w("ScheduledMessageService", "WhatsApp send failed for message ID: " + longExtra + ", reason=" + stringExtra);
            }
        }
        q();
        k();
        if (m(this)) {
            return;
        }
        b();
        stopSelf();
    }

    public final void h() {
        if (this.x) {
            return;
        }
        try {
            ContextCompat.registerReceiver(this, this.y, new IntentFilter("com.wmods.wppenhacer.MESSAGE_SENT"), 2);
            this.x = true;
            Log.d("ScheduledMessageService", "Message result receiver registered");
        } catch (Exception e) {
            Log.e("ScheduledMessageService", "Failed to register message result receiver", e);
        }
    }

    public final void i() {
        AlarmManager alarmManager = (AlarmManager) getSystemService("alarm");
        if (alarmManager == null) {
            Log.e("ScheduledMessageService", "AlarmManager is null");
            return;
        }
        Intent intent = new Intent(this, (Class<?>) ScheduledMessageService.class);
        intent.setAction("com.wmods.wppenhacer.CHECK_SCHEDULED_MESSAGES");
        PendingIntent service = PendingIntent.getService(this, 0, intent, 201326592);
        long jCurrentTimeMillis = System.currentTimeMillis() + 30000;
        try {
            if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(0, jCurrentTimeMillis, service);
            } else {
                alarmManager.setAndAllowWhileIdle(0, jCurrentTimeMillis, service);
                Log.w("ScheduledMessageService", "Using inexact alarm - exact alarms not permitted");
            }
            Log.d("ScheduledMessageService", "Next check scheduled at " + f(jCurrentTimeMillis));
        } catch (SecurityException e) {
            Log.e("ScheduledMessageService", "Security exception scheduling alarm", e);
            alarmManager.set(0, jCurrentTimeMillis, service);
        }
    }

    public final void j(long j) {
        ScheduledMessage ScheduledMessageVarX = this.q.x(j);
        if (ScheduledMessageVarX == null || !ScheduledMessageVarX.v()) {
            Log.d("ScheduledMessageService", "Skipping send for inactive or missing message ID: " + j);
            return;
        }
        if (ScheduledMessageVarX.w()) {
            Log.d("ScheduledMessageService", "Message already waiting for WhatsApp callback, skipping ID: " + j);
            return;
        }
        Log.d("ScheduledMessageService", "Attempting to send message ID: " + j);
        if (!this.q.J(j)) {
            Log.w("ScheduledMessageService", "Failed to mark message as dispatching: " + j);
            return;
        }
        boolean zB = ScheduledMessageHelper.b(this, j);
        Log.d("ScheduledMessageService", "Message " + j + " dispatch result: " + zB);
        if (!zB) {
            this.q.b(j);
        }
        q();
        k();
    }

    public final void k() {
        List<ScheduledMessage> listO = this.q.o();
        boolean z2 = !listO.isEmpty();
        int size = listO.size();
        Iterator<ScheduledMessage> it = listO.iterator();
        boolean z3 = false;
        boolean z4 = false;
        while (it.hasNext()) {
            if (it.next().t() == 1) {
                z4 = true;
            } else {
                z3 = true;
            }
            if (z3 && z4) {
                break;
            }
        }
        l(z2, size, z3, z4);
    }

    public final void l(boolean z2, int i, boolean z3, boolean z4) {
        Intent intent = new Intent("com.wmods.wppenhacer.SCHEDULED_MESSAGES_STATUS");
        intent.putExtra("has_pending", z2);
        intent.putExtra("pending_count", i);
        intent.putExtra("needs_whatsapp", z3);
        intent.putExtra("needs_business", z4);
        sendBroadcast(intent);
        Log.d("ScheduledMessageService", "Sent scheduled messages status: hasPending=" + z2 + ", count=" + i + ", needsWhatsapp=" + z3 + ", needsBusiness=" + z4);
    }

    public final void o() {
        if (this.x) {
            try {
                unregisterReceiver(this.y);
            } catch (Exception e) {
                Log.e("ScheduledMessageService", "Failed to unregister message result receiver", e);
            } finally {
                this.x = false;
            }
        }
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        this.q = ScheduledMessageDatabase.v(this);
        e();
        h();
        try {
            int i = Build.VERSION.SDK_INT;
            if (i >= 34) {
                ServiceCompat.startForeground(this, 1001, d(0), 1);
            } else if (i >= 29) {
                startForeground(1001, d(0), 1);
            } else {
                startForeground(1001, d(0));
            }
            this.s = true;
            Log.d("ScheduledMessageService", "Service created and foreground started");
        } catch (Exception e) {
            Log.e("ScheduledMessageService", "Failed to start foreground service", e);
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        this.s = false;
        o();
        Log.d("ScheduledMessageService", "Service destroyed");
        if (!m(this)) {
            l(false, 0, false, false);
            return;
        }
        k();
        Log.d("ScheduledMessageService", "Active messages remain, requesting restart");
        n(this);
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        String strB = "ScheduledMessageService";
        StringBuilder sb = new StringBuilder();
        sb.append("onStartCommand called with action: ");
        sb.append(intent != null ? intent.getAction() : "null");
        Log.d(strB, sb.toString());
        if (intent != null) {
            String action = intent.getAction();
            if ("com.wmods.wppenhacer.SEND_SCHEDULED_MESSAGE".equals(action)) {
                long longExtra = intent.getLongExtra("message_id", -1L);
                if (longExtra != -1) {
                    j(longExtra);
                }
            } else if ("com.wmods.wppenhacer.CHECK_SCHEDULED_MESSAGES".equals(action)) {
                c();
            }
        } else {
            c();
        }
        if (m(this)) {
            i();
            return 1;
        }
        Log.d("ScheduledMessageService", "No more active messages, stopping service");
        b();
        stopSelf();
        return 2;
    }

    @Override // android.app.Service
    public void onTaskRemoved(Intent intent) {
        super.onTaskRemoved(intent);
        Log.d("ScheduledMessageService", "Task removed, checking if should restart");
        if (m(this)) {
            Log.d("ScheduledMessageService", "Active messages remain after task removed, restarting");
            n(this);
        }
    }

    public final void p(int i, ScheduledMessage ScheduledMessageVar) {
        String string;
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.setFlags(335544320);
        PendingIntent activity = PendingIntent.getActivity(this, 0, intent, 67108864);
        String string2 = getString(R.string.scheduled_messages);
        if (i == 0) {
            string = getString(R.string.scheduled_messages_monitoring);
        } else if (ScheduledMessageVar != null) {
            string = getString(R.string.scheduled_messages_next, Integer.valueOf(i), ScheduledMessageVar.g(), f(ScheduledMessageVar.p()));
        } else {
            string = getString(R.string.scheduled_messages_active, Integer.valueOf(i));
        }
        Notification notificationB = new NotificationCompat.Builder(this, H0).setContentTitle(string2).setContentText(string).setSmallIcon(R.drawable.ic_schedule).setPriority(-1).setOngoing(true).setContentIntent(activity).setCategory("service").setVisibility(1).setStyle(new NotificationCompat.BigTextStyle().bigText(string)).build();
        NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
        if (notificationManager != null) {
            notificationManager.notify(1001, notificationB);
        }
    }

    public final void q() {
        List<ScheduledMessage> listO = this.q.o();
        ScheduledMessage ScheduledMessageVar = null;
        long j = Long.MAX_VALUE;
        for (ScheduledMessage ScheduledMessageVar2 : listO) {
            long jP = ScheduledMessageVar2.p();
            if (jP < j && !ScheduledMessageVar2.J()) {
                ScheduledMessageVar = ScheduledMessageVar2;
                j = jP;
            }
        }
        p(listO.size(), ScheduledMessageVar);
    }
}
