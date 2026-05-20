package com.wmods.wppenhacer.xposed.features.media;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONArray;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallRecording extends Feature {

    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isCallConnected = new AtomicBoolean(false);
    private final AtomicReference<MediaRecorder> mediaRecorderRef = new AtomicReference<>();
    private final AtomicReference<ParcelFileDescriptor> outputPfdRef = new AtomicReference<>();
    private final AtomicReference<FileOutputStream> outputStreamRef = new AtomicReference<>();
    private final AtomicReference<File> outputFileRef = new AtomicReference<>();
    private final AtomicReference<FMessageWpp.UserJid> currentUserJid = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> delayedStartFuture = new AtomicReference<>();

    private final ScheduledExecutorService delayedStartScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "WaEnhancer-CallDelayedStart");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean permissionGranted = new AtomicBoolean(false);

    private final AtomicReference<Activity> activeVoipActivity = new AtomicReference<>();
    private final AtomicBoolean isVideoRecordingPreferred = new AtomicBoolean(false);
    private final AtomicReference<Process> activeVideoProcess = new AtomicReference<>();
    private final AtomicReference<File> videoOutputFileRef = new AtomicReference<>();
    private BroadcastReceiver videoStatusReceiver = null;

    public CallRecording(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("call_recording_enable", false)) {
            logDebug("WaEnhancer: Call Recording is disabled");
            return;
        }

        logDebug("WaEnhancer: Call Recording feature initializing...");

        if (prefs.getBoolean("call_recording_use_root", false) && prefs.getBoolean("call_recording_video_enabled", false)) {
            Context context = FeatureLoader.mApp;
            if (context != null) {
                videoStatusReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        if (intent == null || !"com.wmods.wppenhacer.CALL_RECORDING_VIDEO_STATUS".equals(intent.getAction())) {
                            return;
                        }
                        String status = intent.getStringExtra("status");
                        String sessionId = intent.getStringExtra("session_id");
                        logDebug("WaEnhancer: Video Status Broadcast: " + status + ", session=" + sessionId);
                    }
                };
                IntentFilter filter = new IntentFilter("com.wmods.wppenhacer.CALL_RECORDING_VIDEO_STATUS");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(videoStatusReceiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(videoStatusReceiver, filter);
                }
            }
        }

        hookCallStateChanges();
    }

    private void hookCallStateChanges() {
        int hooksInstalled = 0;

        try {
            Class<?> clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                    "VoiceServiceEventCallback");
            if (clsCallEventCallback != null) {
                logDebug("WaEnhancer: Found VoiceServiceEventCallback: " + clsCallEventCallback.getName());

                try {
                    XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            handleCallEnded("fieldstatsReady");
                        }
                    });
                    hooksInstalled++;
                } catch (Throwable e) {
                    logDebug("WaEnhancer: Could not hook fieldstatsReady: " + e.getMessage());
                }

                XposedBridge.hookAllMethods(clsCallEventCallback, "soundPortCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        logDebug("WaEnhancer: soundPortCreated - will check call recording mode");
                        extractUserJid(param.thisObject);
                        isCallConnected.set(true);

                        String mode = prefs.getString("call_recording_mode", "automatic");
                        if ("automatic".equals(mode)) {
                            logDebug("WaEnhancer: Automatic mode active, starting recording after 3s");
                            scheduleDelayedStart();
                        } else {
                            logDebug("WaEnhancer: Manual mode active, waiting for floating button click");
                        }
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            logDebug("WaEnhancer: Could not hook VoiceServiceEventCallback: " + e.getMessage());
        }

        try {
            Class<?> voipActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains,
                    "VoipActivity");
            if (voipActivityClass != null && Activity.class.isAssignableFrom(voipActivityClass)) {
                logDebug("WaEnhancer: Found VoipActivity: " + voipActivityClass.getName());

                XposedBridge.hookAllMethods(voipActivityClass, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        activeVoipActivity.set(activity);
                        setupFloatingButtons(activity);
                    }
                });

                XposedBridge.hookAllMethods(voipActivityClass, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        activeVoipActivity.set(activity);
                        setupFloatingButtons(activity);
                    }
                });

                XposedBridge.hookAllMethods(voipActivityClass, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        removeFloatingButtons(activity);
                        activeVoipActivity.set(null);
                        handleCallEnded("VoipActivity.onDestroy");
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            logDebug("WaEnhancer: Could not hook VoipActivity: " + e.getMessage());
        }

        logDebug("WaEnhancer: Call Recording initialized with " + hooksInstalled + " hooks");
    }

    private void handleCallEnded(@NonNull String reason) {
        logDebug("WaEnhancer: Call ended by " + reason);
        isCallConnected.set(false);
        cancelDelayedStart();
        stopRecording();
    }

    private void setupFloatingButtons(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        String mode = prefs.getString("call_recording_mode", "automatic");
        if (!"manual".equals(mode)) {
            removeFloatingButtons(activity);
            return;
        }

        try {
            activity.runOnUiThread(() -> {
                android.widget.FrameLayout frameLayout = activity.findViewById(android.R.id.content);
                if (frameLayout == null) return;

                int headerId = activity.getResources().getIdentifier("call_screen_header_view", "id", activity.getPackageName());
                final View headerView = headerId != 0 ? frameLayout.findViewById(headerId) : null;
                if (headerView == null) return;

                android.widget.ImageButton btnRecord = frameLayout.findViewWithTag("call_recording_button");
                if (btnRecord == null) {
                    btnRecord = new android.widget.ImageButton(activity);
                    btnRecord.setTag("call_recording_button");
                    btnRecord.setBackgroundColor(0);
                    btnRecord.setPadding(0, 0, 0, 0);
                    btnRecord.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);

                    android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                            Utils.dipToPixels(42.0f), Utils.dipToPixels(42.0f));
                    lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                    lp.leftMargin = Utils.dipToPixels(12.0f);
                    frameLayout.addView(btnRecord, lp);

                    btnRecord.setOnClickListener(v -> {
                        if (isRecording.get()) {
                            stopRecording();
                        } else {
                            startRecording();
                        }
                        updateFloatingButtonsState();
                    });
                }

                boolean videoOptionEnabled = prefs.getBoolean("call_recording_use_root", false) &&
                        prefs.getBoolean("call_recording_video_enabled", false);
                
                android.widget.ImageButton btnToggle = frameLayout.findViewWithTag("call_recording_mode_toggle");
                if (btnToggle == null && videoOptionEnabled) {
                    btnToggle = new android.widget.ImageButton(activity);
                    btnToggle.setTag("call_recording_mode_toggle");
                    btnToggle.setBackgroundColor(0);
                    btnToggle.setPadding(0, 0, 0, 0);
                    btnToggle.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);

                    android.widget.FrameLayout.LayoutParams lp2 = new android.widget.FrameLayout.LayoutParams(
                            Utils.dipToPixels(32.0f), Utils.dipToPixels(32.0f));
                    lp2.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                    lp2.leftMargin = Utils.dipToPixels(60.0f);
                    frameLayout.addView(btnToggle, lp2);

                    btnToggle.setOnClickListener(v -> {
                        isVideoRecordingPreferred.set(!isVideoRecordingPreferred.get());
                        updateFloatingButtonsState();
                    });
                } else if (btnToggle != null && !videoOptionEnabled) {
                    frameLayout.removeView(btnToggle);
                    btnToggle = null;
                }

                android.widget.TextView txtStatus = frameLayout.findViewWithTag("call_recording_status");
                if (txtStatus == null) {
                    txtStatus = new android.widget.TextView(activity);
                    txtStatus.setTag("call_recording_status");
                    txtStatus.setTextColor(0xFFFFFFFF);
                    txtStatus.setTextSize(12.0f);
                    txtStatus.setSingleLine(true);
                    txtStatus.setGravity(android.view.Gravity.CENTER);
                    txtStatus.setVisibility(android.view.View.GONE);

                    android.widget.FrameLayout.LayoutParams lp3 = new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                    lp3.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                    frameLayout.addView(txtStatus, lp3);
                }

                final View finalBtnRecord = btnRecord;
                final View finalBtnToggle = btnToggle;
                final View finalTxtStatus = txtStatus;

                headerView.post(() -> {
                    int top = headerView.getTop() + headerView.getHeight() + Utils.dipToPixels(8.0f);

                    android.view.ViewGroup.LayoutParams lp = finalBtnRecord.getLayoutParams();
                    if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                        ((android.widget.FrameLayout.LayoutParams) lp).topMargin = top;
                        finalBtnRecord.setLayoutParams(lp);
                    }

                    if (finalBtnToggle != null) {
                        android.view.ViewGroup.LayoutParams lp2 = finalBtnToggle.getLayoutParams();
                        if (lp2 instanceof android.widget.FrameLayout.LayoutParams) {
                            ((android.widget.FrameLayout.LayoutParams) lp2).topMargin =
                                    top + ((finalBtnRecord.getHeight() - finalBtnToggle.getHeight()) / 2);
                            finalBtnToggle.setLayoutParams(lp2);
                        }
                    }

                    android.view.ViewGroup.LayoutParams lp3 = finalTxtStatus.getLayoutParams();
                    if (lp3 instanceof android.widget.FrameLayout.LayoutParams) {
                        ((android.widget.FrameLayout.LayoutParams) lp3).topMargin = top;
                        finalTxtStatus.setLayoutParams(lp3);
                    }
                });

                updateFloatingButtonsState();
            });
        } catch (Throwable e) {
            logDebug("WaEnhancer: setupFloatingButtons error: " + e.getMessage());
        }
    }

    private void updateFloatingButtonsState() {
        Activity activity = activeVoipActivity.get();
        if (activity == null || activity.isFinishing()) return;

        try {
            activity.runOnUiThread(() -> {
                android.widget.FrameLayout frameLayout = activity.findViewById(android.R.id.content);
                if (frameLayout == null) return;

                android.widget.ImageButton btnRecord = frameLayout.findViewWithTag("call_recording_button");
                android.widget.ImageButton btnToggle = frameLayout.findViewWithTag("call_recording_mode_toggle");
                android.widget.TextView txtStatus = frameLayout.findViewWithTag("call_recording_status");

                if (isRecording.get()) {
                    if (btnRecord != null) btnRecord.setVisibility(android.view.View.GONE);
                    if (btnToggle != null) btnToggle.setVisibility(android.view.View.GONE);
                    
                    if (txtStatus != null) {
                        txtStatus.setText("REC");
                        txtStatus.setTextColor(0xFFFF4444);
                        txtStatus.setVisibility(android.view.View.VISIBLE);
                    }
                } else {
                    if (btnRecord != null) {
                        btnRecord.setVisibility(android.view.View.VISIBLE);
                        btnRecord.setImageDrawable(createCircularIndicatorDrawable(activity, 0xFF4CAF50, "REC"));
                    }
                    if (btnToggle != null) {
                        btnToggle.setVisibility(android.view.View.VISIBLE);
                        if (isVideoRecordingPreferred.get()) {
                            btnToggle.setImageDrawable(createCircularIndicatorDrawable(activity, 0xFF2196F3, "VID"));
                        } else {
                            btnToggle.setImageDrawable(createCircularIndicatorDrawable(activity, 0xFF9C27B0, "AUD"));
                        }
                    }
                    if (txtStatus != null) {
                        txtStatus.setVisibility(android.view.View.GONE);
                    }
                }
            });
        } catch (Throwable e) {
            logDebug("WaEnhancer: updateFloatingButtonsState error: " + e.getMessage());
        }
    }

    private void removeFloatingButtons(Activity activity) {
        if (activity == null) return;
        try {
            activity.runOnUiThread(() -> {
                android.widget.FrameLayout frameLayout = activity.findViewById(android.R.id.content);
                if (frameLayout == null) return;

                android.view.View btnRecord = frameLayout.findViewWithTag("call_recording_button");
                if (btnRecord != null) frameLayout.removeView(btnRecord);

                android.view.View btnToggle = frameLayout.findViewWithTag("call_recording_mode_toggle");
                if (btnToggle != null) frameLayout.removeView(btnToggle);

                android.view.View txtStatus = frameLayout.findViewWithTag("call_recording_status");
                if (txtStatus != null) frameLayout.removeView(txtStatus);
            });
        } catch (Throwable e) {
            logDebug("WaEnhancer: removeFloatingButtons error: " + e.getMessage());
        }
    }

    private android.graphics.drawable.Drawable createCircularIndicatorDrawable(Context context, int bgColor, String text) {
        int size = Utils.dipToPixels(42.0f);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(bgColor);
        canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f - Utils.dipToPixels(2.0f), paint);

        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeWidth(Utils.dipToPixels(2.0f));
        canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f - Utils.dipToPixels(2.0f), paint);

        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setTextSize(Utils.dipToPixels(12.0f));
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);

        float yPos = (canvas.getHeight() / 2.0f) - ((paint.descent() + paint.ascent()) / 2.0f);
        canvas.drawText(text, size / 2.0f, yPos, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private String shellEscape(String str) {
        if (str.isEmpty()) return "''";
        return "'" + str.replace("'", "'\\''") + "'";
    }

    private void grantVoiceCallPermission() {
        if (permissionGranted.get())
            return;

        try {
            String packageName = FeatureLoader.mApp.getPackageName();
            logDebug("WaEnhancer: Granting CAPTURE_AUDIO_OUTPUT via root");

            String[] commands = {
                    "pm grant " + packageName + " android.permission.CAPTURE_AUDIO_OUTPUT",
                    "appops set " + packageName + " RECORD_AUDIO allow",
            };

            for (String cmd : commands) {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    int exitCode = process.waitFor();
                    logDebug("WaEnhancer: " + cmd + " exit: " + exitCode);
                } catch (Exception e) {
                    logDebug("WaEnhancer: Root failed: " + e.getMessage());
                }
            }

            permissionGranted.set(true);
        } catch (Throwable e) {
            logDebug("WaEnhancer: grantVoiceCallPermission error: " + e.getMessage());
        }
    }

    private void scheduleDelayedStart() {
        cancelDelayedStart();
        ScheduledFuture<?> future = delayedStartScheduler.schedule(() -> {
            if (!isCallConnected.get()) {
                logDebug("WaEnhancer: Delayed start cancelled, call not connected");
                return;
            }
            if (isRecording.get()) {
                logDebug("WaEnhancer: Delayed start ignored, already recording");
                return;
            }
            startRecording();
        }, 3, TimeUnit.SECONDS);
        delayedStartFuture.set(future);
    }

    private void cancelDelayedStart() {
        ScheduledFuture<?> future = delayedStartFuture.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void extractUserJid(Object callback) {
        if (callback == null) return;

        try {
            Object callInfo = XposedHelpers.callMethod(callback, "getCallInfo");
            if (callInfo == null) return;

            Object peerJid = null;
            try {
                peerJid = XposedHelpers.getObjectField(callInfo, "peerJid");
            } catch (Throwable ignored) {
            }

            if (peerJid != null) {
                FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(peerJid);
                if (!userJid.isNull()) {
                    currentUserJid.set(userJid);
                    logDebug("WaEnhancer: Found phone from UserJid: " + userJid.getPhoneNumber());
                    return;
                }
            }

            Object participantsObj = null;
            try {
                participantsObj = XposedHelpers.getObjectField(callInfo, "participants");
            } catch (Throwable ignored) {
            }

            if (participantsObj instanceof Map<?, ?>) {
                Map<?, ?> participants = (Map<?, ?>) participantsObj;
                for (Object key : participants.keySet()) {
                    FMessageWpp.UserJid userJid2 = new FMessageWpp.UserJid(key);
                    if (!userJid2.isNull()) {
                        currentUserJid.set(userJid2);
                        logDebug("WaEnhancer: Found phone from single participant: " + userJid2.getPhoneNumber());
                        return;
                    }
                }
            }
        } catch (Throwable e) {
            logDebug("WaEnhancer: extractUserJid error: " + e.getMessage());
        }
    }

    private synchronized void startRecording() {
        if (isRecording.get()) {
            logDebug("WaEnhancer: Already recording");
            return;
        }

        FMessageWpp.UserJid cUserJid = currentUserJid.get();
        if (cUserJid != null) {
            if (!shouldRecord(cUserJid.getPhoneNumber())) {
                logDebug("WaEnhancer: Skipping recording due to privacy settings for: " + cUserJid.getPhoneNumber());
                return;
            }
        }

        if (!isCallConnected.get()) {
            logDebug("WaEnhancer: Skipping recording, call is not connected");
            return;
        }

        try {
            if (ContextCompat.checkSelfPermission(FeatureLoader.mApp,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                logDebug("WaEnhancer: No RECORD_AUDIO permission");
                return;
            }

            WaeIIFace bridge = null;
            try {
                bridge = WppCore.getClientBridge();
            } catch (Throwable t) {
                logDebug("WaEnhancer: Could not get client bridge: " + t.getMessage());
            }

            String packageName = FeatureLoader.mApp.getPackageName();
            String appName = packageName.contains("w4b") ? "WA Business" : "WhatsApp";
            String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String settingsPath = prefs.getString("call_recording_path", defaultPath);

            File parentDir = new File(settingsPath, "WA Call Recordings");
            File appDir = new File(parentDir, appName);

            if (bridge != null) {
                if (!appDir.exists() && !appDir.mkdirs()) {
                    boolean dirCreated = bridge.createDir(appDir.getAbsolutePath());
                    if (!dirCreated && !appDir.exists()) {
                        throw new IOException("Could not create output directory: " + appDir.getAbsolutePath());
                    }
                }
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "Call_" + timestamp + ".m4a";

            if (cUserJid != null) {
                String contactName = WppCore.getContactName(cUserJid);
                if (contactName.isEmpty())
                    fileName = "Call_" + cUserJid.getPhoneNumber() + "_" + timestamp + ".m4a";
                else fileName = "Call_" + contactName + "_" + timestamp + ".m4a";
            }

            OutputTarget outputTarget = openOutputTarget(bridge, appDir, fileName);

            outputFileRef.set(outputTarget.file);
            outputPfdRef.set(outputTarget.parcelFileDescriptor);
            outputStreamRef.set(outputTarget.outputStream);

            boolean useRoot = prefs.getBoolean("call_recording_use_root", false);
            boolean videoEnabled = prefs.getBoolean("call_recording_video_enabled", false) && useRoot;
            boolean shouldRecordVideo = false;

            String mode = prefs.getString("call_recording_mode", "automatic");
            if (videoEnabled) {
                if ("manual".equals(mode)) {
                    shouldRecordVideo = isVideoRecordingPreferred.get();
                } else {
                    shouldRecordVideo = true;
                }
            }

            // Launch Root Video Recording if requested and enabled
            if (shouldRecordVideo) {
                try {
                    String apkPath = FeatureLoader.modulePath;
                    try {
                        Context context = FeatureLoader.mApp;
                        if (context != null) {
                            apkPath = context.getPackageManager().getApplicationInfo("com.wmods.wppenhacer", 0).sourceDir;
                        }
                    } catch (Exception e) {
                        logDebug("WaEnhancer: Failed to resolve apkPath dynamically: " + e.getMessage());
                    }
                    int myPid = android.os.Process.myPid();
                    String videoSessionId = String.valueOf(System.currentTimeMillis());

                    File videoTempFile = new File(outputTarget.file.getParent(),
                            outputTarget.file.getName().replace(".m4a", "_video.mp4").replace(".wav", "_video.mp4"));
                    videoOutputFileRef.set(videoTempFile);

                    String videoQuality = prefs.getString("call_recording_video_quality", "medium");
                    int fps = 30;
                    try {
                        fps = (int) Float.parseFloat(prefs.getString("call_recording_video_fps", "30.0"));
                    } catch (NumberFormatException ignored) {}

                    int maxEdge = 1080;
                    int bitrate = 4000000;
                    if ("high".equals(videoQuality)) {
                        bitrate = 8000000;
                        maxEdge = 1440;
                    } else if ("low".equals(videoQuality)) {
                        bitrate = 2000000;
                        maxEdge = 720;
                    } else if ("custom".equals(videoQuality)) {
                        bitrate = prefs.getInt("call_recording_video_bitrate", 4000000);
                        maxEdge = prefs.getInt("call_recording_video_max_size", 1080);
                    }

                    String codec = prefs.getString("call_recording_video_codec", "h264");

                    StringBuilder cmd = new StringBuilder();
                    cmd.append("CLASSPATH=").append(shellEscape(apkPath)).append(" ");
                    cmd.append("app_process / com.wmods.wppenhacer.xposed.bridge.video.RootVideoRecordingServer ");
                    cmd.append("--session-id ").append(shellEscape(videoSessionId)).append(" ");
                    cmd.append("--target-package ").append(shellEscape(packageName)).append(" ");
                    cmd.append("--target-pid ").append(myPid).append(" ");
                    cmd.append("--output-path ").append(shellEscape(videoTempFile.getAbsolutePath())).append(" ");
                    cmd.append("--bitrate ").append(bitrate).append(" ");
                    cmd.append("--fps ").append(fps).append(" ");
                    cmd.append("--max-size ").append(maxEdge).append(" ");
                    cmd.append("--codec ").append(shellEscape(codec));

                    logDebug("WaEnhancer: Launching video server command: " + cmd.toString());
                    bridge = WppCore.getClientBridge();
                    if (bridge != null) {
                        boolean ok = bridge.startVideoRootServer(cmd.toString());
                        logDebug("WaEnhancer: Video server launch via bridge status: " + ok);
                    } else {
                        logDebug("WaEnhancer: Bridge unavailable, falling back to local su execution");
                        Process videoProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd.toString()});
                        activeVideoProcess.set(videoProcess);
                    }
                } catch (Exception e) {
                    logDebug("WaEnhancer: Failed to start root video server: " + e.getMessage());
                }
            }

            if (useRoot) {
                String sessionId = String.valueOf(System.currentTimeMillis());
                String audioQuality = prefs.getString("call_recording_audio_quality", "DEFAULT");
                String micSources = prefs.getString("call_recording_mic_sources", "VOICE_COMMUNICATION,MIC");
                String usages = prefs.getString("call_recording_usages", "USAGE_VOICE_COMMUNICATION,USAGE_MEDIA");
                boolean globalCapture = prefs.getBoolean("call_recording_global_capture", false);
                boolean advanced = prefs.getBoolean("call_recording_advanced_enabled", true);

                boolean started = RootRecordingManager.startRootServer(
                        sessionId, outputTarget.file.getAbsolutePath(),
                        audioQuality, micSources, usages, globalCapture, advanced);

                if (started) {
                    isRecording.set(true);
                    logDebug("WaEnhancer: Root Recording Server started for: " + outputTarget.file.getAbsolutePath());
                    if (prefs.getBoolean("call_recording_toast", false)) {
                        Utils.showToast("Root Recording started", Toast.LENGTH_SHORT);
                    }
                    updateFloatingButtonsState();
                    return;
                }
            }

            int[] audioSources = new int[]{
                    MediaRecorder.AudioSource.VOICE_CALL,
                    MediaRecorder.AudioSource.VOICE_UPLINK,
                    MediaRecorder.AudioSource.VOICE_DOWNLINK,
                    6,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC
            };
            String[] sourceNames = new String[]{
                    "VOICE_CALL", "VOICE_UPLINK", "VOICE_DOWNLINK", "VOICE_RECOGNITION",
                    "VOICE_COMMUNICATION", "MIC"
            };

            MediaRecorder selectedRecorder = null;
            String usedSource = "none";

            for (int i = 0; i < audioSources.length; i++) {
                MediaRecorder testRecorder = new MediaRecorder();
                try {
                    logDebug("WaEnhancer: Trying " + sourceNames[i]);
                    testRecorder.setAudioSource(audioSources[i]);
                    testRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    testRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    testRecorder.setAudioEncodingBitRate(96000);
                    testRecorder.setAudioSamplingRate(44100);
                    testRecorder.setOutputFile(outputTarget.fd);

                    testRecorder.prepare();
                    testRecorder.start();

                    selectedRecorder = testRecorder;
                    usedSource = sourceNames[i];
                    logDebug("WaEnhancer: SUCCESS " + sourceNames[i]);
                    break;
                } catch (Exception e) {
                    logDebug("WaEnhancer: FAILED " + sourceNames[i] + ": " + e.getMessage());
                    try {
                        testRecorder.reset();
                        testRecorder.release();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (selectedRecorder == null) {
                logDebug("WaEnhancer: All audio sources failed");
                closeOutputResources(false);
                return;
            }

            mediaRecorderRef.set(selectedRecorder);
            if (!isRecording.compareAndSet(false, true)) {
                try {
                    selectedRecorder.stop();
                } catch (RuntimeException ignored) {
                }
                selectedRecorder.reset();
                selectedRecorder.release();
                mediaRecorderRef.set(null);
                closeOutputResources(false);
                return;
            }

            logDebug("WaEnhancer: Recording started (" + usedSource + "): " + outputTarget.file.getAbsolutePath());

            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast("Recording started", Toast.LENGTH_SHORT);
            }

            updateFloatingButtonsState();

        } catch (Exception e) {
            logDebug("WaEnhancer: startRecording error: " + e.getMessage());
            isRecording.set(false);
            MediaRecorder rec = mediaRecorderRef.getAndSet(null);
            if (rec != null) {
                try {
                    rec.reset();
                    rec.release();
                } catch (Throwable ignored) {
                }
            }
            closeOutputResources(true);
            updateFloatingButtonsState();
        }
    }

    private synchronized void stopRecording() {
        cancelDelayedStart();
        if (!isRecording.getAndSet(false))
            return;

        boolean saved = false;
        try {
            MediaRecorder recorder = mediaRecorderRef.getAndSet(null);
            if (recorder != null) {
                try {
                    recorder.stop();
                    saved = true;
                } catch (RuntimeException e) {
                    logDebug("WaEnhancer: MediaRecorder stop exception (no valid audio data received)");
                } finally {
                    try {
                        recorder.reset();
                        recorder.release();
                    } catch (Exception ignored) {
                    }
                }
            } else if (prefs.getBoolean("call_recording_use_root", false)) {
                RootRecordingManager.stopRootServer();
                saved = true;
            }

            File outputFile = outputFileRef.getAndSet(null);
            closeOutputResources(!saved);

            WaeIIFace bridge = WppCore.getClientBridge();
            if (bridge != null) {
                try {
                    bridge.stopVideoRootServer();
                } catch (Exception ignored) {}
            }
            Process videoProc = activeVideoProcess.getAndSet(null);
            if (videoProc != null) {
                try {
                    videoProc.destroy();
                } catch (Exception ignored) {}
            }

            File videoFile = videoOutputFileRef.getAndSet(null);

            logDebug("WaEnhancer: Recording stopped, file=" + (outputFile != null ? outputFile.getAbsolutePath() : "unknown"));

            if (saved && outputFile != null) {
                if (videoFile != null && videoFile.exists()) {
                    muxAudioVideo(outputFile, videoFile);
                } else {
                    Utils.scanFile(outputFile);
                    if (prefs.getBoolean("call_recording_toast", false)) {
                        Utils.showToast("Recording saved!", Toast.LENGTH_SHORT);
                    }
                }
            } else {
                if (prefs.getBoolean("call_recording_toast", false)) {
                    Utils.showToast("Recording failed", Toast.LENGTH_SHORT);
                }
            }

            currentUserJid.set(null);
            updateFloatingButtonsState();
        } catch (Exception e) {
            logDebug("WaEnhancer: stopRecording error: " + e.getMessage());
            closeOutputResources(false);
            outputFileRef.set(null);
            updateFloatingButtonsState();
        }
    }

    private void muxAudioVideo(final File audioFile, final File videoFile) {
        new Thread(() -> {
            android.media.MediaExtractor videoExtractor = null;
            android.media.MediaExtractor audioExtractor = null;
            android.media.MediaMuxer muxer = null;
            boolean success = false;

            File parentDir = audioFile.getParentFile();
            String name = audioFile.getName();
            String finalName = name.replace(".m4a", ".mp4").replace(".wav", ".mp4");
            File finalOutputFile = new File(parentDir, finalName);

            try {
                logDebug("WaEnhancer: Starting MediaMuxer...");

                videoExtractor = new android.media.MediaExtractor();
                videoExtractor.setDataSource(videoFile.getAbsolutePath());

                audioExtractor = new android.media.MediaExtractor();
                audioExtractor.setDataSource(audioFile.getAbsolutePath());

                muxer = new android.media.MediaMuxer(finalOutputFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                int videoTrackIndex = -1;
                for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                    android.media.MediaFormat format = videoExtractor.getTrackFormat(i);
                    String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                    if (mime.startsWith("video/")) {
                        videoExtractor.selectTrack(i);
                        videoTrackIndex = muxer.addTrack(format);
                        break;
                    }
                }

                int audioTrackIndex = -1;
                for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                    android.media.MediaFormat format = audioExtractor.getTrackFormat(i);
                    String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio/")) {
                        audioExtractor.selectTrack(i);
                        audioTrackIndex = muxer.addTrack(format);
                        break;
                    }
                }

                if (videoTrackIndex == -1 || audioTrackIndex == -1) {
                    throw new IOException("Missing audio or video track in temporary files");
                }

                muxer.start();

                android.media.MediaCodec.BufferInfo videoBufferInfo = new android.media.MediaCodec.BufferInfo();
                java.nio.ByteBuffer videoBuffer = java.nio.ByteBuffer.allocate(1024 * 1024);
                while (true) {
                    videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, 0);
                    if (videoBufferInfo.size < 0) {
                        break;
                    }
                    videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                    videoBufferInfo.flags = videoExtractor.getSampleFlags();
                    videoBufferInfo.offset = 0;
                    muxer.writeSampleData(videoTrackIndex, videoBuffer, videoBufferInfo);
                    videoExtractor.advance();
                }

                android.media.MediaCodec.BufferInfo audioBufferInfo = new android.media.MediaCodec.BufferInfo();
                java.nio.ByteBuffer audioBuffer = java.nio.ByteBuffer.allocate(1024 * 1024);
                while (true) {
                    audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0);
                    if (audioBufferInfo.size < 0) {
                        break;
                    }
                    audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    audioBufferInfo.flags = audioExtractor.getSampleFlags();
                    audioBufferInfo.offset = 0;
                    muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo);
                    audioExtractor.advance();
                }

                success = true;
                logDebug("WaEnhancer: MediaMuxer finished successfully! Saved to: " + finalOutputFile.getAbsolutePath());
            } catch (Exception e) {
                logDebug("WaEnhancer: MediaMuxer failed: " + e.getMessage());
            } finally {
                try {
                    if (videoExtractor != null) videoExtractor.release();
                } catch (Exception ignored) {}
                try {
                    if (audioExtractor != null) audioExtractor.release();
                } catch (Exception ignored) {}
                try {
                    if (muxer != null) {
                        if (success) {
                            muxer.stop();
                        }
                        muxer.release();
                    }
                } catch (Exception ignored) {}

                if (success) {
                    if (videoFile.exists()) videoFile.delete();
                    if (audioFile.exists()) audioFile.delete();
                    Utils.scanFile(finalOutputFile);
                    if (prefs.getBoolean("call_recording_toast", false)) {
                        Utils.showToast("Call recording saved with video!", Toast.LENGTH_SHORT);
                    }
                } else {
                    Utils.scanFile(audioFile);
                    Utils.scanFile(videoFile);
                }
            }
        }, "WaEnhancer-VideoMux").start();
    }

    private void closeOutputResources(boolean deleteOutputFile) {
        FileOutputStream stream = outputStreamRef.getAndSet(null);
        ParcelFileDescriptor pfd = outputPfdRef.getAndSet(null);
        File outputFile = outputFileRef.getAndSet(null);

        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
        }

        if (deleteOutputFile && outputFile != null && outputFile.exists() && !outputFile.delete()) {
            logDebug("WaEnhancer: Could not delete incomplete recording: " + outputFile.getAbsolutePath());
        }
    }

    @NonNull
    private OutputTarget openOutputTarget(WaeIIFace bridge, @NonNull File preferredDir, @NonNull String fileName) throws IOException {
        File preferredFile = new File(preferredDir, fileName);
        if (bridge != null) {
            try {
                ParcelFileDescriptor parcelFileDescriptor = bridge.openFile(preferredFile.getAbsolutePath(), true);
                if (parcelFileDescriptor != null) {
                    return new OutputTarget(preferredFile, parcelFileDescriptor, null, parcelFileDescriptor.getFileDescriptor());
                }
                logDebug("WaEnhancer: Bridge openFile returned null, fallback to Android/data path");
            } catch (Throwable t) {
                logDebug("WaEnhancer: Bridge openFile failed, fallback to Android/data path: " + t.getMessage());
            }
        }

        File appExternalDir = FeatureLoader.mApp.getExternalFilesDir(null);
        if (appExternalDir == null) {
            throw new IOException("Could not resolve app external files directory");
        }
        File fallbackDir = new File(appExternalDir, "Recordings");
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
            throw new IOException("Could not create fallback recording directory: " + fallbackDir.getAbsolutePath());
        }

        File fallbackFile = new File(fallbackDir, fileName);
        FileOutputStream fallbackStream = new FileOutputStream(fallbackFile);
        logDebug("WaEnhancer: Recording fallback path in Android/data: " + fallbackFile.getAbsolutePath());

        return new OutputTarget(fallbackFile, null, fallbackStream, fallbackStream.getFD());
    }

    private static final class OutputTarget {
        @NonNull
        private final File file;
        private final ParcelFileDescriptor parcelFileDescriptor;
        private final FileOutputStream outputStream;
        @NonNull
        private final java.io.FileDescriptor fd;

        private OutputTarget(@NonNull File file, ParcelFileDescriptor parcelFileDescriptor, FileOutputStream outputStream, @NonNull java.io.FileDescriptor fd) {
            this.file = file;
            this.parcelFileDescriptor = parcelFileDescriptor;
            this.outputStream = outputStream;
            this.fd = fd;
        }
    }

    private boolean shouldRecord(String phoneNumber) {
        return true;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Call Recording";
    }
}