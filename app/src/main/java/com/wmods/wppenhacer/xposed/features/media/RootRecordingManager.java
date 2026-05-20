package com.wmods.wppenhacer.xposed.features.media;

import android.content.Context;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class RootRecordingManager {

    private static Process activeRootProcess = null;

    public static boolean startRootServer(String sessionId, String outputPath, String audioQuality, String micSources, String usages, boolean isGlobal, boolean isAdvanced) {
        try {
            Context ctx = FeatureLoader.mApp;
            if (ctx == null) return false;

            File dexFile = new File(ctx.getFilesDir(), "wae_root_server.dex");
            try (InputStream in = ctx.getAssets().open("root_server.dex");
                 OutputStream out = new FileOutputStream(dexFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            String apkPath = FeatureLoader.modulePath;
            String packageName = ctx.getPackageName();
            
            // Build command arguments based on RootCallRecordingServer expectations
            StringBuilder cmd = new StringBuilder();
            cmd.append("export CLASSPATH=").append(apkPath).append(":").append(dexFile.getAbsolutePath()).append(" && ");
            cmd.append("app_process / com.wmods.wppenhacer.xposed.bridge.RootCallRecordingServer");
            cmd.append(" --session-id ").append(sessionId);
            cmd.append(" --target-package ").append(packageName);
            cmd.append(" --target-pid ").append(android.os.Process.myPid());
            cmd.append(" --output-path ").append(outputPath);
            cmd.append(" --format aac");
            cmd.append(" --audio-quality ").append(audioQuality);
            cmd.append(" --mic-sources ").append(micSources);
            cmd.append(" --usages ").append(usages);
            
            if (isGlobal) cmd.append(" --global-capture true");
            if (isAdvanced) cmd.append(" --advanced-enabled true");

            com.wmods.wppenhacer.xposed.bridge.WaeIIFace bridge = com.wmods.wppenhacer.xposed.core.WppCore.getClientBridge();
            if (bridge != null) {
                return bridge.startAudioRootServer(cmd.toString());
            } else {
                activeRootProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd.toString()});
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void stopRootServer() {
        try {
            com.wmods.wppenhacer.xposed.bridge.WaeIIFace bridge = com.wmods.wppenhacer.xposed.core.WppCore.getClientBridge();
            if (bridge != null) {
                bridge.stopAudioRootServer();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (activeRootProcess != null) {
            try {
                activeRootProcess.destroy();
                activeRootProcess = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
