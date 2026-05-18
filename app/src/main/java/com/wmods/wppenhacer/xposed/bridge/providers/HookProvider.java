package com.wmods.wppenhacer.xposed.bridge.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.bridge.service.HookBinder;

public class HookProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (method.equals("getHookBinder")) {
            Bundle result = new Bundle();
            result.putBinder("binder", HookBinder.getInstance());
            return result;
        }
        if ("record_crash".equals(method) && extras != null) {
            String stacktrace = extras.getString("stacktrace");
            if (stacktrace != null && !stacktrace.isEmpty()) {
                try {
                    java.io.File crashDir = getContext().getExternalFilesDir("CrashLogs");
                    if (crashDir != null) {
                        if (!crashDir.exists()) crashDir.mkdirs();
                        
                        // Save new crash
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US);
                        String filename = "crash_" + sdf.format(new java.util.Date()) + ".txt";
                        java.io.File crashFile = new java.io.File(crashDir, filename);
                        try (java.io.FileWriter writer = new java.io.FileWriter(crashFile)) {
                            writer.write("WhatsApp Crash Log\n");
                            writer.write("Date: " + new java.util.Date().toString() + "\n\n");
                            writer.write(stacktrace);
                        }

                        // Auto-delete logs older than 7 days
                        long cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                        java.io.File[] files = crashDir.listFiles();
                        if (files != null) {
                            for (java.io.File f : files) {
                                if (f.isFile() && f.lastModified() < cutoff) {
                                    f.delete();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("WAE_Provider", "Failed to write crash log: " + e.getMessage());
                }
            }
            return Bundle.EMPTY;
        }
        return null;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
