package com.wmods.wppenhacer.xposed.bridge.video;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Locale;

public final class RootVideoRecordingServer {

    private static final String TAG = "RootVideoRecordingServer";
    private static final String ACTION_STATUS = "com.wmods.wppenhacer.CALL_RECORDING_VIDEO_STATUS";

    private static Context systemContext;
    private static Handler mainHandler;
    private static ScreenVideoCaptureProvider videoCaptureProvider;
    private static boolean isRunning = false;

    private static String sessionId;
    private static String targetPackage;
    private static int targetPid = -1;
    private static String outputPath;
    private static int bitrate = 4000000;
    private static int fps = 30;
    private static int maxSize = 1080;
    private static String codec = "h264";

    public static void main(String[] args) {
        try {
            logInfo("Starting RootVideoRecordingServer...");
            parseArguments(args);

            if (sessionId == null || targetPackage == null || targetPid <= 0 || outputPath == null) {
                logError("Invalid arguments. Required: --session-id, --target-package, --target-pid, --output-path");
                System.exit(1);
            }

            // Exemption for Hidden APIs on modern Android
            exemptHiddenApis();

            // Initialize ActivityThread and system Context
            initializeSystemContext();

            // Prepare Main Looper
            Looper.prepare();
            synchronized (Looper.class) {
                Field declaredField = Looper.class.getDeclaredField("sMainLooper");
                declaredField.setAccessible(true);
                declaredField.set(null, Looper.myLooper());
            }
            mainHandler = new Handler(Looper.getMainLooper());

            // Set up Stdin Monitor thread to handle clean exit when parent process dies or closes pipe
            startStdinMonitor();

            // Register Shutdown Hook to clean up recording processes on JVM exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logInfo("Shutdown hook triggered, cleaning up...");
                stopRecording("shutdown");
            }));

            // Verify if target WhatsApp PID belongs to the target package name
            if (!isTargetProcessValid(targetPid, targetPackage)) {
                logError("Target PID " + targetPid + " does not belong to " + targetPackage);
                sendStatusBroadcast("error", 2, "Target process invalid", null);
                System.exit(1);
            }

            // Start recording
            isRunning = true;
            if (startRecording()) {
                sendStatusBroadcast("started", 0, null, null);
                
                // Monitor target process loop
                new Thread(RootVideoRecordingServer::monitorTargetProcess, "TargetMonitor").start();
                
                Looper.loop();
            } else {
                logError("Failed to start video recording provider");
                sendStatusBroadcast("error", 4, "Failed to start video recording provider", null);
                System.exit(1);
            }

        } catch (Throwable t) {
            logError("Fatal error in RootVideoRecordingServer: " + t.getMessage());
            t.printStackTrace();
            sendStatusBroadcast("error", 1, t.getMessage(), null);
            System.exit(1);
        }
    }

    private static void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--session-id".equals(arg) && i + 1 < args.length) {
                sessionId = args[++i];
            } else if ("--target-package".equals(arg) && i + 1 < args.length) {
                targetPackage = args[++i];
            } else if ("--target-pid".equals(arg) && i + 1 < args.length) {
                try {
                    targetPid = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--output-path".equals(arg) && i + 1 < args.length) {
                outputPath = args[++i];
            } else if ("--bitrate".equals(arg) && i + 1 < args.length) {
                try {
                    bitrate = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--fps".equals(arg) && i + 1 < args.length) {
                try {
                    fps = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--max-size".equals(arg) && i + 1 < args.length) {
                try {
                    maxSize = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--codec".equals(arg) && i + 1 < args.length) {
                codec = args[++i];
            }
        }
    }

    private static void exemptHiddenApis() {
        try {
            Object runtime = Class.forName("dalvik.system.VMRuntime")
                    .getDeclaredMethod("getRuntime")
                    .invoke(null);
            if (runtime != null) {
                runtime.getClass()
                        .getDeclaredMethod("setHiddenApiExemptions", String[].class)
                        .invoke(runtime, new Object[]{new String[]{"L"}});
                logInfo("Exempted hidden API checks");
            }
        } catch (Throwable t) {
            logInfo("VMRuntime Hidden API exemption failed (non-fatal): " + t.getMessage());
        }
    }

    private static void initializeSystemContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Constructor<?> constructor = activityThreadClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object activityThread = constructor.newInstance();

            Field sCurrentActivityThread = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThread.setAccessible(true);
            sCurrentActivityThread.set(null, activityThread);

            Field mSystemThread = activityThreadClass.getDeclaredField("mSystemThread");
            mSystemThread.setAccessible(true);
            mSystemThread.setBoolean(activityThread, true);

            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    Constructor<?> configControllerConstructor = Class.forName("android.app.ConfigurationController")
                            .getDeclaredConstructor(Class.forName("android.app.ActivityThreadInternal"));
                    configControllerConstructor.setAccessible(true);
                    Object configController = configControllerConstructor.newInstance(activityThread);
                    Field mConfigurationController = activityThreadClass.getDeclaredField("mConfigurationController");
                    mConfigurationController.setAccessible(true);
                    mConfigurationController.set(activityThread, configController);
                } catch (Throwable th) {
                    logInfo("fillConfigurationController failed (non-fatal): " + th.getMessage());
                }
            }

            try {
                android.app.Instrumentation instrumentation = new android.app.Instrumentation();
                Field mInstrumentation = activityThreadClass.getDeclaredField("mInstrumentation");
                mInstrumentation.setAccessible(true);
                mInstrumentation.set(activityThread, instrumentation);
            } catch (Throwable th) {
                logError("fillInstrumentation failed: " + th.getMessage());
                throw new RuntimeException(th);
            }

            try {
                Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
                Constructor<?> appBindDataConstructor = appBindDataClass.getDeclaredConstructor();
                appBindDataConstructor.setAccessible(true);
                Object appBindData = appBindDataConstructor.newInstance();
                
                android.content.pm.ApplicationInfo applicationInfo = new android.content.pm.ApplicationInfo();
                applicationInfo.packageName = "com.android.shell";
                
                Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
                appInfoField.setAccessible(true);
                appInfoField.set(appBindData, applicationInfo);
                
                Field mBoundApplication = activityThreadClass.getDeclaredField("mBoundApplication");
                mBoundApplication.setAccessible(true);
                mBoundApplication.set(activityThread, appBindData);
            } catch (Throwable th) {
                logInfo("fillAppInfo failed (non-fatal): " + th.getMessage());
            }

            Object contextObj = activityThreadClass.getDeclaredMethod("getSystemContext").invoke(activityThread);
            if (contextObj instanceof Context) {
                systemContext = (Context) contextObj;
                logInfo("Successfully obtained system context");
                
                try {
                    android.app.Application initialApplication = android.app.Instrumentation.newApplication(
                            android.app.Application.class, systemContext);
                    Field mInitialApplication = activityThreadClass.getDeclaredField("mInitialApplication");
                    mInitialApplication.setAccessible(true);
                    mInitialApplication.set(activityThread, initialApplication);
                } catch (Throwable th) {
                    logInfo("fillAppContext failed (non-fatal): " + th.getMessage());
                }
            } else {
                throw new RuntimeException("getSystemContext returned invalid type");
            }
        } catch (Throwable t) {
            logError("Failed to initialize ActivityThread/Context workaround: " + t.getMessage());
            throw new RuntimeException(t);
        }
    }

    private static boolean isTargetProcessValid(int pid, String packageName) {
        if (pid <= 0 || packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            Os.kill(pid, 0); // Check if process exists
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.ESRCH) {
                return false;
            }
        }
        try (FileInputStream fis = new FileInputStream("/proc/" + pid + "/cmdline")) {
            byte[] buffer = new byte[256];
            int bytesRead = fis.read(buffer);
            if (bytesRead <= 0) {
                return false;
            }
            int length = 0;
            while (length < bytesRead && buffer[length] != 0) {
                length++;
            }
            String runningProcessName = new String(buffer, 0, length, java.nio.charset.StandardCharsets.UTF_8);
            boolean match = runningProcessName.equals(packageName) || runningProcessName.startsWith(packageName + ":");
            if (!match) {
                logInfo("Process package name mismatch: " + runningProcessName + " != " + packageName);
            }
            return match;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean startRecording() {
        try {
            VideoCodec parsedCodec = VideoCodec.H264;
            String lowerCodec = codec.toLowerCase(Locale.ROOT);
            if (lowerCodec.contains("h265") || lowerCodec.contains("hevc")) {
                parsedCodec = VideoCodec.H265;
            } else if (lowerCodec.contains("av1")) {
                parsedCodec = VideoCodec.AV1;
            }

            VideoCaptureConfig config = new VideoCaptureConfig(
                    0, // Default primary display (displayId = 0)
                    new File(outputPath),
                    maxSize,
                    bitrate,
                    fps,
                    2, // Default I-Frame interval
                    parsedCodec
            );

            logInfo("Initializing ScreenVideoCaptureProvider...");
            videoCaptureProvider = new ScreenVideoCaptureProvider();
            videoCaptureProvider.start(config);

            // Wait a short time to verify it didn't exit immediately due to error
            Thread.sleep(1500);
            if (!videoCaptureProvider.isRunning()) {
                logError("ScreenVideoCaptureProvider failed to run or exited immediately.");
                return false;
            }
            return true;
        } catch (Exception e) {
            logError("Exception in startRecording: " + e.getMessage());
            return false;
        }
    }

    private static synchronized void stopRecording(String reason) {
        if (videoCaptureProvider != null) {
            try {
                logInfo("Stopping ScreenVideoCaptureProvider (reason=" + reason + ")...");
                videoCaptureProvider.stop(3000);
                videoCaptureProvider = null;
            } catch (Exception e) {
                logError("Failed to stop ScreenVideoCaptureProvider: " + e.getMessage());
            }
        }
        if (isRunning) {
            isRunning = false;
            sendStatusBroadcast("stopped", 0, null, reason);
            
            // Quit looper to let main exit cleanly
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    Looper mainLooper = Looper.getMainLooper();
                    if (mainLooper != null) {
                        mainLooper.quitSafely();
                    }
                });
            }
        }
    }

    private static void startStdinMonitor() {
        Thread monitorThread = new Thread(() -> {
            try {
                logInfo("Starting stdin monitor...");
                while (System.in.read() != -1) {
                    // loop until stdin is closed
                }
                logInfo("stdin closed, stopping video recording...");
                stopRecording("requested");
            } catch (Exception e) {
                logError("Error in stdin monitor: " + e.getMessage());
            }
        }, "RootVideoServer-StdinMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private static void monitorTargetProcess() {
        logInfo("Started target process monitoring...");
        while (isRunning) {
            if (!isTargetProcessValid(targetPid, targetPackage)) {
                logInfo("Target package " + targetPackage + " (PID " + targetPid + ") exited. Stopping recording.");
                stopRecording("target_exited");
                break;
            }
            if (videoCaptureProvider != null && !videoCaptureProvider.isRunning()) {
                logInfo("ScreenVideoCaptureProvider stopped running unexpectedly.");
                stopRecording("unexpected_stop");
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void sendStatusBroadcast(String status, int errorCode, String errorMessage, String stopReason) {
        if (systemContext == null) {
            logError("Cannot send broadcast: system context is null");
            return;
        }
        try {
            Intent intent = new Intent(ACTION_STATUS);
            intent.setPackage(targetPackage);
            intent.putExtra("session_id", sessionId);
            intent.putExtra("target_package", targetPackage);
            intent.putExtra("target_pid", targetPid);
            intent.putExtra("status", status);
            intent.putExtra("error_code", errorCode);
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                intent.putExtra("error_message", errorMessage);
            }
            if (stopReason != null && !stopReason.isEmpty()) {
                intent.putExtra("stop_reason", stopReason);
            }

            systemContext.sendBroadcast(intent);
            logInfo("Sent status broadcast: status=" + status + ", session=" + sessionId);
        } catch (Exception e) {
            logError("Failed to send status broadcast: " + e.getMessage());
        }
    }

    private static void logInfo(String message) {
        Log.i(TAG, message);
        System.out.println(TAG + " [INFO]: " + message);
    }

    private static void logError(String message) {
        Log.e(TAG, message);
        System.err.println(TAG + " [ERROR]: " + message);
    }
}
