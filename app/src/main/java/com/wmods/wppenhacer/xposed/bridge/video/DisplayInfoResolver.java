package com.wmods.wppenhacer.xposed.bridge.video;

import java.lang.reflect.Field;

public final class DisplayInfoResolver {
    public static DisplayCaptureInfo resolve(int displayId) throws Exception {
        Class<?> displayManagerGlobalClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Object instance = displayManagerGlobalClass.getMethod("getInstance").invoke(null);
        Object displayInfo = displayManagerGlobalClass.getMethod("getDisplayInfo", int.class).invoke(instance, displayId);
        if (displayInfo == null) {
            throw new IllegalArgumentException("Display not found: " + displayId);
        }

        int logicalWidth = getIntField(displayInfo, "logicalWidth");
        int logicalHeight = getIntField(displayInfo, "logicalHeight");
        int rotation = getIntFieldWithDefault(displayInfo, "rotation", 0);
        int layerStack = getIntFieldWithDefault(displayInfo, "layerStack", displayId);
        int densityDpi = getIntFieldWithDefault(displayInfo, "logicalDensityDpi", 420);

        return new DisplayCaptureInfo(displayId, logicalWidth, logicalHeight, rotation, layerStack, densityDpi);
    }

    private static int getIntField(Object obj, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(obj);
    }

    private static int getIntFieldWithDefault(Object obj, String fieldName, int defaultValue) {
        try {
            return getIntField(obj, fieldName);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
