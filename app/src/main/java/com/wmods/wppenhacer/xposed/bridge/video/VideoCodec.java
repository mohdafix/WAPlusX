package com.wmods.wppenhacer.xposed.bridge.video;

public enum VideoCodec {
    H264("video/avc"),
    H265("video/hevc"),
    AV1("video/av01");

    private final String mimeType;

    VideoCodec(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
