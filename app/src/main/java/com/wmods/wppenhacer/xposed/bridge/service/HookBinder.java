package com.wmods.wppenhacer.xposed.bridge.service;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HookBinder extends WaeIIFace.Stub {

    private static HookBinder mInstance;

    public static HookBinder getInstance() {
        if (mInstance == null) {
            mInstance = new HookBinder();
        }
        return mInstance;
    }

    @Override
    public ParcelFileDescriptor openFile(String path, boolean create) throws RemoteException {
        File file = new File(path);
        if (!file.exists() && create) {
            try {
                file.createNewFile();
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public boolean createDir(String path) throws RemoteException {
        File file = new File(path);
        return file.mkdirs();
    }

    @Override
    public boolean exists(String path) throws RemoteException {
        return new File(path).exists();
    }

    @Override
    public List listFiles(String path) throws RemoteException {
        var files = new File(path).listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(files);
    }

    private Process activeAudioProcess = null;
    private Process activeVideoProcess = null;

    @Override
    public boolean startAudioRootServer(String command) throws RemoteException {
        try {
            stopAudioRootServer();
            activeAudioProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void stopAudioRootServer() throws RemoteException {
        if (activeAudioProcess != null) {
            try {
                activeAudioProcess.getOutputStream().close();
                boolean exited = activeAudioProcess.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    activeAudioProcess.destroyForcibly();
                }
            } catch (Exception ignored) {}
            activeAudioProcess = null;
        }
    }

    @Override
    public boolean startVideoRootServer(String command) throws RemoteException {
        try {
            stopVideoRootServer();
            activeVideoProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void stopVideoRootServer() throws RemoteException {
        if (activeVideoProcess != null) {
            try {
                // Close stdin to trigger graceful shutdown via the StdinMonitor thread
                activeVideoProcess.getOutputStream().close();
                // Wait up to 6 seconds for the video server to finalize the MP4
                // (it needs ~3s for MediaMuxer.stop() + time to write MOOV atom)
                boolean exited = activeVideoProcess.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    activeVideoProcess.destroyForcibly();
                }
            } catch (Exception ignored) {}
            activeVideoProcess = null;
        }
    }
}
