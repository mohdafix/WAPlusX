package com.wmods.wppenhacer.xposed.bridge.video;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class RootVideoRecordingServer {

    private static final String TAG = "RootVideoRecordingServer";
    private static final String ACTION_STATUS = "com.wmods.wppenhacer.CALL_RECORDING_VIDEO_STATUS";

    private static Context systemContext;
    private static Handler mainHandler;
    private static java.lang.Process activeScreenrecordProcess;
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

            // Kill any previously orphaned screenrecord sessions to avoid conflicts
            cleanupOrphanedScreenrecord();

            // Register Shutdown Hook to clean up recording processes on JVM exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logInfo("Shutdown hook triggered, cleaning up...");
                stopRecordingProcess();
                sendStatusBroadcast("stopped", 0, null, "shutdown");
            }));

            // Verify if target WhatsApp PID belongs to the target package name
            if (!isTargetProcessValid(targetPid, targetPackage)) {
                logError("Target PID " + targetPid + " does not belong to " + targetPackage);
                sendStatusBroadcast("error", 2, "Target process invalid", null);
                System.exit(1);
            }

            // Start recording
            isRunning = true;
            if (startRecordingProcess()) {
                sendStatusBroadcast("started", 0, null, null);
                
                // Monitor target process loop
                new Thread(RootVideoRecordingServer::monitorTargetProcess, "TargetMonitor").start();
                
                Looper.loop();
            } else {
                logError("Failed to start screenrecord process");
                sendStatusBroadcast("error", 4, "Failed to start screenrecord process", null);
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

            Object contextObj = activityThreadClass.getDeclaredMethod("getSystemContext").invoke(activityThread);
            if (contextObj instanceof Context) {
                systemContext = (Context) contextObj;
                logInfo("Successfully obtained system context");
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

    private static void cleanupOrphanedScreenrecord() {
        try {
            logInfo("Cleaning up any running screenrecord instances...");
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"pkill", "-9", "screenrecord"});
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private static boolean startRecordingProcess() {
        try {
            File outFile = new File(outputPath);
            File parentDir = outFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                logError("Failed to create parent directories for video output");
                return false;
            }

            // Clean up any existing file at output path
            if (outFile.exists() && !outFile.delete()) {
                logInfo("Could not delete existing file at: " + outputPath);
            }

            // Query native physical screen size
            String physicalSize = getPhysicalScreenSize();
            String recordSize = calculateRecordSize(physicalSize, maxSize);

            // Construct screenrecord command
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("/system/bin/screenrecord");
            command.add("--bit-rate");
            command.add(String.valueOf(bitrate));
            
            if (recordSize != null) {
                command.add("--size");
                command.add(recordSize);
            }
            
            command.add("--time-limit");
            command.add("3600"); // Up to 1 hour
            
            command.add(outputPath);

            logInfo("Launching screenrecord: " + command.toString());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            activeScreenrecordProcess = builder.start();

            // Read output stream of screenrecord in a separate thread for diagnostic logging
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeScreenrecordProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logInfo("[screenrecord] " + line);
                    }
                } catch (Exception ignored) {}
            }, "ScreenrecordLogger").start();

            // Wait a short time to verify it didn't exit immediately due to error
            Thread.sleep(800);
            try {
                int exitValue = activeScreenrecordProcess.exitValue();
                logError("screenrecord process exited immediately with: " + exitValue);
                return false;
            } catch (IllegalThreadStateException ignored) {
                // Process is still running, which is what we want!
                return true;
            }

        } catch (Exception e) {
            logError("Exception in startRecordingProcess: " + e.getMessage());
            return false;
        }
    }

    private static void stopRecordingProcess() {
        if (activeScreenrecordProcess != null) {
            try {
                logInfo("Stopping active screenrecord process...");
                // screenrecord finishes cleanly on SIGINT or standard process termination in Java
                activeScreenrecordProcess.destroy();
                activeScreenrecordProcess = null;
            } catch (Exception e) {
                logError("Failed to destroy screenrecord process: " + e.getMessage());
            }
        }
    }

    private static String getPhysicalScreenSize() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("wm size");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains("Physical size:")) {
                    return line.substring(line.indexOf(":") + 1).trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String calculateRecordSize(String physicalSize, int maxEdgeSize) {
        if (physicalSize == null || !physicalSize.contains("x")) {
            return null;
        }
        try {
            String[] parts = physicalSize.split("x");
            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());
            int maxDimension = Math.max(w, h);
            
            if (maxDimension <= maxEdgeSize) {
                // Ensure dimensions are multiples of 2
                w = (w / 2) * 2;
                h = (h / 2) * 2;
                return w + "x" + h;
            }
            
            double scale = (double) maxEdgeSize / maxDimension;
            int newW = (int) Math.round(w * scale);
            int newH = (int) Math.round(h * scale);
            
            // screenrecord requires width and height to be even
            newW = (newW / 2) * 2;
            newH = (newH / 2) * 2;
            return newW + "x" + newH;
        } catch (Exception ignored) {}
        return null;
    }

    private static void monitorTargetProcess() {
        logInfo("Started target process monitoring...");
        while (isRunning) {
            if (!isTargetProcessValid(targetPid, targetPackage)) {
                logInfo("Target package " + targetPackage + " (PID " + targetPid + ") exited. Stopping recording.");
                isRunning = false;
                stopRecordingProcess();
                sendStatusBroadcast("stopped", 0, null, "target_exited");
                
                // Quit looper to let main exit cleanly
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        Looper mainLooper = Looper.getMainLooper();
                        if (mainLooper != null) {
                            mainLooper.quitSafely();
                        }
                    });
                }
                break;
            }
            try {
                Thread.sleep(1000);
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
