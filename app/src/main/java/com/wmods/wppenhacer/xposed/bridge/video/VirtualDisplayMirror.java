package com.wmods.wppenhacer.xposed.bridge.video;

import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.Surface;
import java.lang.reflect.Method;

public final class VirtualDisplayMirror implements DisplayMirror {
    private static final String TAG = "VirtualDisplayMirror";
    private VirtualDisplay virtualDisplay;

    @Override
    public void setupMirror(DisplayCaptureInfo displayInfo, int width, int height, Surface surface) throws Exception {
        Class<?> displayManagerClass = Class.forName("android.hardware.display.DisplayManager");
        Method createVirtualDisplayMethod = displayManagerClass.getDeclaredMethod(
                "createVirtualDisplay",
                String.class, int.class, int.class, int.class, Surface.class
        );
        createVirtualDisplayMethod.setAccessible(true);
        Object vd = createVirtualDisplayMethod.invoke(
                null,
                "wmods_screen_capture",
                width,
                height,
                displayInfo.getDisplayId(),
                surface
        );
        if (vd instanceof VirtualDisplay) {
            this.virtualDisplay = (VirtualDisplay) vd;
        } else {
            throw new IllegalArgumentException("Hidden DisplayManager returned invalid VirtualDisplay");
        }
    }

    @Override
    public void close() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release VirtualDisplay: " + t.getMessage());
            } finally {
                virtualDisplay = null;
            }
        }
    }
}
