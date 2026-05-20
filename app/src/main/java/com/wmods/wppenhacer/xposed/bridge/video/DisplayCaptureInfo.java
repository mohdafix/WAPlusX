package com.wmods.wppenhacer.xposed.bridge.video;

public final class DisplayCaptureInfo {
    private final int displayId;
    private final int width;
    private final int height;
    private final int rotation;
    private final int layerStack;
    private final int densityDpi;

    public DisplayCaptureInfo(int displayId, int width, int height, int rotation, int layerStack, int densityDpi) {
        this.displayId = displayId;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.layerStack = layerStack;
        this.densityDpi = densityDpi;
    }

    public int getDisplayId() {
        return displayId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRotation() {
        return rotation;
    }

    public int getLayerStack() {
        return layerStack;
    }

    public int getDensityDpi() {
        return densityDpi;
    }

    @Override
    public String toString() {
        return "DisplayCaptureInfo(displayId=" + displayId + ", width=" + width + ", height=" + height + ", rotation=" + rotation + ", layerStack=" + layerStack + ", densityDpi=" + densityDpi + ")";
    }
}
