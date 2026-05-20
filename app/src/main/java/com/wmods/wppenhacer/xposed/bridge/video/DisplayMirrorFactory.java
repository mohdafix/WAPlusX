package com.wmods.wppenhacer.xposed.bridge.video;

import android.util.Log;
import android.view.Surface;

public final class DisplayMirrorFactory implements DisplayMirror {
    private static final String TAG = "DisplayMirrorFactory";
    private final DisplayMirror primary;
    private final DisplayMirror fallback;
    private DisplayMirror activeMirror;

    public DisplayMirrorFactory() {
        this.primary = new VirtualDisplayMirror();
        DisplayMirror fb = null;
        try {
            fb = new SurfaceControlMirror();
        } catch (Throwable t) {
            Log.e(TAG, "SurfaceControlMirror instantiation failed: " + t.getMessage());
        }
        this.fallback = fb;
    }

    @Override
    public void setupMirror(DisplayCaptureInfo displayInfo, int width, int height, Surface surface) throws Exception {
        try {
            primary.setupMirror(displayInfo, width, height, surface);
            activeMirror = primary;
            Log.i(TAG, "Display mirrored using hidden DisplayManager");
        } catch (Throwable t) {
            Log.w(TAG, "Hidden DisplayManager mirror failed: " + t.getMessage());
            if (fallback != null) {
                fallback.setupMirror(displayInfo, width, height, surface);
                activeMirror = fallback;
                Log.i(TAG, "Display mirrored using SurfaceControl fallback");
            } else {
                throw new IllegalStateException("DisplayManager mirror failed and SurfaceControl fallback is not available", t);
            }
        }
    }

    @Override
    public void close() {
        if (activeMirror != null) {
            try {
                activeMirror.close();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to close active display mirror: " + t.getMessage());
            } finally {
                activeMirror = null;
            }
        }
    }
}
