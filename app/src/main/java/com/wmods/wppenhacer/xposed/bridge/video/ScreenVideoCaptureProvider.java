package com.wmods.wppenhacer.xposed.bridge.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScreenVideoCaptureProvider {
    private static final String TAG = "ScreenVideoCaptureProvider";
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile Thread captureThread;
    private volatile MediaCodec encoder;
    private DisplayMirror displayMirror;
    private Surface inputSurface;
    private MediaMuxer mediaMuxer;

    public void start(final VideoCaptureConfig config) {
        validateConfig(config);
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Video capture is already running");
        }
        stopRequested.set(false);

        captureThread = new Thread(() -> {
            try {
                runCaptureLoop(config);
            } catch (Throwable t) {
                Log.e(TAG, "Error during screen capture loop", t);
            } finally {
                cleanup();
                isRunning.set(false);
            }
        }, "RootScreenVideoCapture");
        captureThread.start();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public boolean stop(long timeoutMs) {
        if (!isRunning.get()) {
            return true;
        }
        stopRequested.set(true);
        signalEndOfStream();

        Thread thread = captureThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for video capture to stop");
            }
        }
        boolean finished = !isRunning.get();
        if (!finished) {
            Log.w(TAG, "Video capture did not stop within " + timeoutMs + "ms; MP4 may be incomplete");
        } else {
            captureThread = null;
        }
        return finished;
    }

    private void runCaptureLoop(VideoCaptureConfig config) throws Exception {
        prepareOutputFile(config.getOutputFile());
        
        DisplayCaptureInfo displayInfo = DisplayInfoResolver.resolve(config.getDisplayId());
        VideoSize targetSize = VideoSize.calculate(displayInfo.getWidth(), displayInfo.getHeight(), config.getMaxSize());
        
        Log.i(TAG, "Starting screen capture: display=" + displayInfo.getDisplayId() 
                + ", source=" + displayInfo.getWidth() + "x" + displayInfo.getHeight() 
                + ", target=" + targetSize.getWidth() + "x" + targetSize.getHeight() 
                + ", codec=" + config.getCodec().getMimeType() 
                + ", bitrate=" + config.getBitRate() + ", fps=" + config.getFrameRate());

        encoder = MediaCodec.createEncoderByType(config.getCodec().getMimeType());
        MediaFormat format = MediaFormat.createVideoFormat(config.getCodec().getMimeType(), targetSize.getWidth(), targetSize.getHeight());
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.getBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.getFrameRate());
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, 2130708361); // Surface color format
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.getIFrameIntervalSeconds());
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        format.setInteger(MediaFormat.KEY_LATENCY, 1);

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();
        if (inputSurface == null) {
            throw new IllegalStateException("Failed to create encoder input surface");
        }

        displayMirror = new DisplayMirrorFactory();
        displayMirror.setupMirror(displayInfo, targetSize.getWidth(), targetSize.getHeight(), inputSurface);

        mediaMuxer = new MediaMuxer(config.getOutputFile().getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        encoder.start();

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int trackIndex = -1;
        boolean muxerStarted = false;

        while (!stopRequested.get()) {
            DrainResult result = drainEncoder(encoder, mediaMuxer, bufferInfo, trackIndex, muxerStarted);
            trackIndex = result.trackIndex;
            muxerStarted = result.muxerStarted;
            if (result.endOfStream) {
                break;
            }
        }

        // Send EOS and drain final frames
        signalEndOfStream();
        drainEncoderFinal(encoder, mediaMuxer, bufferInfo, trackIndex, muxerStarted);

        try {
            config.getOutputFile().setReadable(true, false);
            config.getOutputFile().setWritable(true, false);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set final file permissions: " + e.getMessage());
        }

        Log.i(TAG, "Screen capture saved: " + config.getOutputFile().getAbsolutePath());
    }

    private static class DrainResult {
        final int trackIndex;
        final boolean muxerStarted;
        final boolean endOfStream;

        DrainResult(int trackIndex, boolean muxerStarted, boolean endOfStream) {
            this.trackIndex = trackIndex;
            this.muxerStarted = muxerStarted;
            this.endOfStream = endOfStream;
        }
    }

    private DrainResult drainEncoder(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo bufferInfo, int trackIndex, boolean muxerStarted) {
        int index = codec.dequeueOutputBuffer(bufferInfo, 10000);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (muxerStarted) {
                throw new IllegalStateException("Encoder output format changed after muxer start");
            }
            int newTrack = muxer.addTrack(codec.getOutputFormat());
            muxer.start();
            return new DrainResult(newTrack, true, false);
        } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER || index < 0) {
            return new DrainResult(trackIndex, muxerStarted, false);
        }

        ByteBuffer outputBuffer = codec.getOutputBuffer(index);
        if (outputBuffer != null && muxerStarted && trackIndex != -1 && bufferInfo.size > 0 && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
        }

        boolean eos = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        codec.releaseOutputBuffer(index, false);
        return new DrainResult(trackIndex, muxerStarted, eos);
    }

    private void drainEncoderFinal(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo bufferInfo, int trackIndex, boolean muxerStarted) {
        DrainResult result;
        int maxAttempts = 100; // 100 attempts * 10ms = ~1 second
        int attempts = 0;
        do {
            result = drainEncoder(codec, muxer, bufferInfo, trackIndex, muxerStarted);
            trackIndex = result.trackIndex;
            muxerStarted = result.muxerStarted;
            attempts++;
            if (attempts > maxAttempts) {
                Log.w(TAG, "Timed out waiting for EOS during drainEncoderFinal");
                break;
            }
        } while (!result.endOfStream);
    }

    private void signalEndOfStream() {
        if (encoder != null) {
            try {
                encoder.signalEndOfInputStream();
            } catch (IllegalStateException e) {
                // Already stopped or not started
            }
        }
    }

    private void prepareOutputFile(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Failed to create output directory: " + parent.getAbsolutePath());
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalArgumentException("Failed to overwrite output file: " + file.getAbsolutePath());
        }
        try {
            file.createNewFile();
            file.setReadable(true, false);
            file.setWritable(true, false);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set file permissions: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (displayMirror != null) {
            try {
                displayMirror.close();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to close display mirror", t);
            }
            displayMirror = null;
        }
        if (inputSurface != null) {
            try {
                inputSurface.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release input surface", t);
            }
            inputSurface = null;
        }
        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to stop encoder", t);
            }
            try {
                encoder.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release encoder", t);
            }
            encoder = null;
        }
        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to stop muxer", t);
            }
            try {
                mediaMuxer.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release muxer", t);
            }
            mediaMuxer = null;
        }
    }

    private void validateConfig(VideoCaptureConfig config) {
        if (config.getDisplayId() < 0) {
            throw new IllegalArgumentException("displayId must be >= 0");
        }
        if (config.getMaxSize() < 16) {
            throw new IllegalArgumentException("maxSize is too small");
        }
        if (config.getBitRate() <= 0) {
            throw new IllegalArgumentException("bitRate must be > 0");
        }
        if (config.getFrameRate() <= 0) {
            throw new IllegalArgumentException("frameRate must be > 0");
        }
        if (config.getIFrameIntervalSeconds() <= 0) {
            throw new IllegalArgumentException("iFrameIntervalSeconds must be > 0");
        }
        if (!config.getOutputFile().getName().toLowerCase().endsWith(".mp4")) {
            throw new IllegalArgumentException("outputFile extension must be .mp4");
        }
    }
}
