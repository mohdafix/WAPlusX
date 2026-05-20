package com.wmods.wppenhacer.xposed.bridge.video;

import android.view.Surface;

public interface DisplayMirror extends AutoCloseable {
    void setupMirror(DisplayCaptureInfo displayInfo, int width, int height, Surface surface) throws Exception;
}
