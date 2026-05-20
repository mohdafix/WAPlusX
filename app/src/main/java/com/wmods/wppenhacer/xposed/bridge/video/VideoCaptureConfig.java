package com.wmods.wppenhacer.xposed.bridge.video;

import java.io.File;

public final class VideoCaptureConfig {
    private final int displayId;
    private final File outputFile;
    private final int maxSize;
    private final int bitRate;
    private final int frameRate;
    private final int iFrameIntervalSeconds;
    private final VideoCodec codec;

    public VideoCaptureConfig(int displayId, File outputFile, int maxSize, int bitRate, int frameRate, int iFrameIntervalSeconds, VideoCodec codec) {
        this.displayId = displayId;
        this.outputFile = outputFile;
        this.maxSize = maxSize;
        this.bitRate = bitRate;
        this.frameRate = frameRate;
        this.iFrameIntervalSeconds = iFrameIntervalSeconds;
        this.codec = codec;
    }

    public int getDisplayId() {
        return displayId;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getBitRate() {
        return bitRate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public int getIFrameIntervalSeconds() {
        return iFrameIntervalSeconds;
    }

    public VideoCodec getCodec() {
        return codec;
    }
}
