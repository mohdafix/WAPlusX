package com.wmods.wppenhacer.xposed.features.media;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class VoiceNoteKeepScreenOn extends Feature {

    public VoiceNoteKeepScreenOn(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static final Set<Object> activeVoiceNoteTracks = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean isFeatureLoggingEnabled = true;

    private static void logFeature(String msg) {
        if (isFeatureLoggingEnabled) {
            XposedBridge.log("WaEnhancer[VoiceNoteKeepScreenOn]: " + msg);
        }
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("voice_note_keep_screen_on", false)) {
            return;
        }

        XC_MethodHook playbackHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String methodName = param.method.getName();
                Object track = param.thisObject;

                if (methodName.equals("play") || methodName.equals("start")) {
                    if (isVoiceNoteCaller()) {
                        activeVoiceNoteTracks.add(track);
                    }
                } else {
                    activeVoiceNoteTracks.remove(track);
                }
                updateScreenState();
            }
        };

        // Hook AudioTrack (primary for WhatsApp Voice Notes)
        try {
            XposedHelpers.findAndHookMethod(android.media.AudioTrack.class, "play", playbackHook);
            XposedHelpers.findAndHookMethod(android.media.AudioTrack.class, "stop", playbackHook);
            XposedHelpers.findAndHookMethod(android.media.AudioTrack.class, "pause", playbackHook);
            XposedHelpers.findAndHookMethod(android.media.AudioTrack.class, "release", playbackHook);
        } catch (Throwable t) {
            XposedBridge.log("WaEnhancer: AudioTrack hooks failed: " + t.getMessage());
        }

        // Hook MediaPlayer (Secondary/Fallback)
        try {
            XposedHelpers.findAndHookMethod(android.media.MediaPlayer.class, "start", playbackHook);
            XposedHelpers.findAndHookMethod(android.media.MediaPlayer.class, "stop", playbackHook);
            XposedHelpers.findAndHookMethod(android.media.MediaPlayer.class, "pause", playbackHook);
            XposedHelpers.findAndHookMethod(android.media.MediaPlayer.class, "release", playbackHook);
        } catch (Throwable t) {
        }

        // Handle activity changes to ensure flag is reapplied
        WppCore.addListenerActivity((activity, type) -> {
            if (type == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                updateScreenState(activity);
            }
        });
    }

    private boolean isVoiceNoteCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName().toLowerCase();
            // Check for known WhatsApp audio player/engine classes
            if (className.contains("messageaudioplayer") ||
                    className.contains("heroplayer") ||
                    className.contains("exoplayer") ||
                    className.contains("audiorecord") ||
                    className.contains("voicenote")) {

                // Exclude system notification sounds and logs
                if (className.contains(".util.log") || className.contains(".notification.")) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private synchronized void updateScreenState() {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(() -> {
            Activity activity = WppCore.getCurrentActivity();
            updateScreenState(activity);
        }, 100);
    }

    private void updateScreenState(Activity activity) {
        if (activity == null || activity.isFinishing())
            return;

        boolean isPlaying = !activeVoiceNoteTracks.isEmpty();

        try {
            if (isPlaying && isRelevantActivity(activity)) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isRelevantActivity(Activity activity) {
        String name = activity.getClass().getName();
        return name.contains("Conversation") ||
                name.contains("HomeActivity") ||
                name.contains("StatusPlayback") ||
                name.contains("MediaView");
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "VoiceNoteKeepScreenOn";
    }
}
