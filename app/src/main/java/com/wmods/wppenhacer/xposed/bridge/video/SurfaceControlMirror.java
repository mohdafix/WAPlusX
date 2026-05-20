package com.wmods.wppenhacer.xposed.bridge.video;

import android.graphics.Rect;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import java.lang.reflect.Method;

public final class SurfaceControlMirror implements DisplayMirror {
    private static final String TAG = "SurfaceControlMirror";
    private final Class<?> surfaceControlClass;
    private IBinder displayBinder;

    public SurfaceControlMirror() throws ClassNotFoundException {
        this.surfaceControlClass = Class.forName("android.view.SurfaceControl");
    }

    @Override
    public void setupMirror(DisplayCaptureInfo displayInfo, int width, int height, Surface surface) throws Exception {
        displayBinder = createDisplay("wmods_screen_capture", false);
        Rect layerStackRect = new Rect(0, 0, displayInfo.getWidth(), displayInfo.getHeight());
        Rect displayRect = new Rect(0, 0, width, height);

        try {
            applyTransaction(displayBinder, surface, displayInfo.getRotation(), layerStackRect, displayRect, displayInfo.getLayerStack());
        } catch (Throwable t) {
            Log.w(TAG, "SurfaceControl.Transaction failed, falling back to legacy transaction: " + t.getMessage());
            applyTransactionLegacy(displayBinder, surface, displayInfo.getRotation(), layerStackRect, displayRect, displayInfo.getLayerStack());
        }
    }

    private IBinder createDisplay(String name, boolean secure) throws Exception {
        Method createDisplayMethod = surfaceControlClass.getDeclaredMethod("createDisplay", String.class, boolean.class);
        createDisplayMethod.setAccessible(true);
        Object binder = createDisplayMethod.invoke(null, name, secure);
        if (binder instanceof IBinder) {
            return (IBinder) binder;
        }
        throw new IllegalArgumentException("SurfaceControl.createDisplay returned invalid IBinder");
    }

    private void applyTransaction(IBinder binder, Surface surface, int rotation, Rect layerStackRect, Rect displayRect, int layerStack) throws Exception {
        Class<?> transactionClass = Class.forName("android.view.SurfaceControl$Transaction");
        Object transaction = transactionClass.getDeclaredConstructor().newInstance();
        
        transactionClass.getDeclaredMethod("setDisplaySurface", IBinder.class, Surface.class).invoke(transaction, binder, surface);
        transactionClass.getDeclaredMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class).invoke(transaction, binder, rotation, layerStackRect, displayRect);
        transactionClass.getDeclaredMethod("setDisplayLayerStack", IBinder.class, int.class).invoke(transaction, binder, layerStack);
        transactionClass.getDeclaredMethod("apply").invoke(transaction);
    }

    private void applyTransactionLegacy(IBinder binder, Surface surface, int rotation, Rect layerStackRect, Rect displayRect, int layerStack) throws Exception {
        Method openTransactionMethod = surfaceControlClass.getDeclaredMethod("openTransaction");
        Method closeTransactionMethod = surfaceControlClass.getDeclaredMethod("closeTransaction");
        openTransactionMethod.invoke(null);
        try {
            surfaceControlClass.getDeclaredMethod("setDisplaySurface", IBinder.class, Surface.class).invoke(null, binder, surface);
            surfaceControlClass.getDeclaredMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class).invoke(null, binder, rotation, layerStackRect, displayRect);
            surfaceControlClass.getDeclaredMethod("setDisplayLayerStack", IBinder.class, int.class).invoke(null, binder, layerStack);
        } finally {
            closeTransactionMethod.invoke(null);
        }
    }

    @Override
    public void close() {
        if (displayBinder != null) {
            try {
                surfaceControlClass.getDeclaredMethod("destroyDisplay", IBinder.class).invoke(null, displayBinder);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to destroy SurfaceControl display: " + t.getMessage());
            } finally {
                displayBinder = null;
            }
        }
    }
}
