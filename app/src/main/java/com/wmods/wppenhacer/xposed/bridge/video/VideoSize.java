package com.wmods.wppenhacer.xposed.bridge.video;

public final class VideoSize {
    private final int width;
    private final int height;

    public VideoSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public static VideoSize calculate(int displayWidth, int displayHeight, int maxSize) {
        if (displayWidth <= 0 || displayHeight <= 0 || maxSize <= 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        int maxDimension = Math.max(displayWidth, displayHeight);
        if (maxDimension <= maxSize) {
            return new VideoSize(align(displayWidth), align(displayHeight));
        }
        double scale = (double) maxSize / maxDimension;
        return new VideoSize(align((int) (displayWidth * scale)), align((int) (displayHeight * scale)));
    }

    private static int align(int size) {
        return Math.max(16, size - (size % 8)); // aligned to 8 (often required by codecs)
    }

    @Override
    public String toString() {
        return "VideoSize(width=" + width + ", height=" + height + ")";
    }
}
