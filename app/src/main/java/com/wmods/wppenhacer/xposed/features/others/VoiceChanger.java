package com.wmods.wppenhacer.xposed.features.others;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Voice Changer Feature
 * 
 * Intercepts voice recordings and applies audio effects (pitch shift, tempo
 * change)
 * before WhatsApp sends them.
 * 
 * Hook Strategy:
 * 1. Hook OpusRecorder constructor to capture the output file path
 * 2. Hook OpusRecorder.stop() to trigger voice processing after recording
 * 3. Process the opus file: decode -> apply effects -> re-encode
 * 4. Replace the original file with the processed version
 */
public class VoiceChanger extends Feature {

    private static final String TAG = "VoiceChanger";

    // Voice effect types (matching C++ enum)
    public static final int EFFECT_DISABLED = 0;
    public static final int EFFECT_BABY = 1;
    public static final int EFFECT_TEENAGER = 2;
    public static final int EFFECT_DEEP = 3;
    public static final int EFFECT_ROBOT = 4;
    public static final int EFFECT_DRUNK = 5;
    public static final int EFFECT_FAST = 6;
    public static final int EFFECT_SLOW_MOTION = 7;
    public static final int EFFECT_UNDERWATER = 8;
    public static final int EFFECT_FUN = 9;
    public static final int EFFECT_OPTIMUS = 10;
    public static final int EFFECT_MINION = 11;
    public static final int EFFECT_BANE = 12;
    public static final int EFFECT_FEMALE = 13;
    public static final int EFFECT_MALE = 14;

    public static final int[] EFFECT_RES_IDS = {
            ResId.string.voice_effect_disabled, ResId.string.voice_effect_baby, ResId.string.voice_effect_teenager,
            ResId.string.voice_effect_deep, ResId.string.voice_effect_robot, ResId.string.voice_effect_drunk,
            ResId.string.voice_effect_fast, ResId.string.voice_effect_slow_motion, ResId.string.voice_effect_underwater,
            ResId.string.voice_effect_fun, ResId.string.voice_effect_optimus, ResId.string.voice_effect_minion,
            ResId.string.voice_effect_bane, ResId.string.voice_effect_female, ResId.string.voice_effect_male
    };

    // Menu item ID for conversation menu
    private static final int MENU_ITEM_ID = 0x57A22;

    // Current effect (stored for dynamic updates)
    private static int currentEffect = EFFECT_DISABLED;

    // Current recording file path (captured from constructor)
    private static String currentRecordingPath = null;

    // Track if we've processed the current recording
    private static boolean recordingProcessed = false;

    // Executor for background processing
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Handler for main thread callbacks
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Native library loaded flag
    private static boolean nativeLoaded = false;

    // Static block to load native library
    static {
        try {
            System.loadLibrary("voicechanger");
            nativeLoaded = true;
            nativeInit();
            XposedBridge.log("WaEnhancer: VoiceChanger native library loaded");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("WaEnhancer: Failed to load voicechanger library: " + e.getMessage());
            nativeLoaded = false;
        }
    }

    // Native methods
    private static native boolean nativeInit();

    private static native void nativeRelease();

    private static native void nativeSetEffect(int effectType);

    private static native void nativeSetCustomParams(float tempo, float pitch, float speed);

    private static native short[] nativeProcessAudio(short[] input, int sampleRate);

    private static native boolean nativeIsEnabled();

    public VoiceChanger(ClassLoader classLoader, XSharedPreferences xSharedPreferences) {
        super(classLoader, xSharedPreferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("voice_changer_enabled", false)) {
            log("Voice Changer is disabled in preferences");
            return;
        }

        if (!nativeLoaded) {
            log("Voice Changer native library not loaded, feature disabled");
            return;
        }

        // Load the selected effect from preferences (or from runtime storage)
        String storedEffect = WppCore.getPrivString("voice_changer_current_effect", null);
        if (storedEffect != null) {
            currentEffect = Integer.parseInt(storedEffect);
        } else {
            currentEffect = Integer.parseInt(prefs.getString("voice_changer_effect", "0"));
        }
        nativeSetEffect(currentEffect);
        log("Voice Changer initialized with effect: " + currentEffect + " (" + getEffectName(currentEffect) + ")");

        hookOpusRecorder();
        hookConversationMenu();
    }

    /**
     * Get the effect name for display
     */
    public static String getEffectName(int effectType) {
        if (effectType >= 0 && effectType < EFFECT_RES_IDS.length) {
            int resId = EFFECT_RES_IDS[effectType];
            if (resId != 0) {
                return Utils.getApplication().getString(resId);
            }
        }
        return "Unknown";
    }

    /**
     * Set the voice effect dynamically (can be called from UI)
     */
    public static void setEffect(int effectType) {
        if (effectType >= 0 && effectType < EFFECT_RES_IDS.length) {
            currentEffect = effectType;
            if (nativeLoaded) {
                nativeSetEffect(effectType);
            }
            // Store in runtime preferences so it persists during session
            WppCore.setPrivString("voice_changer_current_effect", String.valueOf(effectType));
            XposedBridge.log("WaEnhancer: Voice Changer effect set to: " + getEffectName(effectType));
        }
    }

    /**
     * Get the current effect type
     */
    public static int getCurrentEffect() {
        return currentEffect;
    }

    /**
     * Hook the conversation menu to add voice changer quick access
     */
    private void hookConversationMenu() throws Exception {
        var onCreateMenuConversationMethod = Unobfuscator.loadBlueOnReplayCreateMenuConversationMethod(classLoader);
        if (onCreateMenuConversationMethod == null) {
            log("Could not find conversation menu method, skipping UI integration");
            return;
        }

        XposedBridge.hookMethod(onCreateMenuConversationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var activity = WppCore.getCurrentConversation();
                if (activity == null)
                    return;
                addVoiceChangerMenuItem(menu, activity);
            }
        });
        log("Voice Changer conversation menu hook installed");
    }

    /**
     * Add voice changer menu item to conversation menu
     */
    private void addVoiceChangerMenuItem(Menu menu, Activity activity) {
        if (menu.findItem(MENU_ITEM_ID) != null)
            return;

        String title = String.format(activity.getString(ResId.string.voice_prefix), getEffectName(currentEffect));
        MenuItem item = menu.add(0, MENU_ITEM_ID, 0, title);

        // Load custom voice changer icon from module resources
        try {
            android.graphics.drawable.Drawable iconDraw = null;
            try {
                android.content.Context modContext = activity.createPackageContext("com.wmods.wppenhacer", android.content.Context.CONTEXT_IGNORE_SECURITY);
                int resId = modContext.getResources().getIdentifier("ic_voice_changer_mic", "drawable", "com.wmods.wppenhacer");
                if (resId != 0) {
                    iconDraw = modContext.getDrawable(resId);
                }
            } catch (Exception ignored) {
            }
            if (iconDraw == null) {
                iconDraw = DesignUtils.getDrawableByName("ic_music");
                if (iconDraw == null) {
                    iconDraw = activity.getDrawable(android.R.drawable.ic_menu_info_details);
                }
            }
            if (iconDraw != null) {
                iconDraw.setTint(DesignUtils.getPrimaryTextColor());
                item.setIcon(iconDraw);
            }
        } catch (Throwable ignored) {
        }

        // Show in action bar if space available
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        item.setOnMenuItemClickListener(mi -> {
            showVoiceChangerDialog(activity, item);
            return true;
        });
    }

    /**
     * Show dialog to select voice effect
     */
    private void showVoiceChangerDialog(Activity activity, MenuItem menuItem) {
        try {
            AlertDialogWpp dialog = new AlertDialogWpp(activity);
            dialog.setTitle(activity.getString(ResId.string.voice_changer));

            android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            int p = Utils.dipToPixels(16);
            root.setPadding(p, p / 2, p, p);
            
            android.widget.ListView listView = new android.widget.ListView(activity);
            listView.setDivider(null);
            listView.setDividerHeight(Utils.dipToPixels(8));
            listView.setSelector(android.R.color.transparent);
            
            listView.setAdapter(new android.widget.BaseAdapter() {
                @Override
                public int getCount() { return EFFECT_RES_IDS.length; }
                @Override
                public Object getItem(int position) { return getEffectName(position); }
                @Override
                public long getItemId(int position) { return position; }
                @Override
                public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.widget.LinearLayout layout;
                    if (convertView == null) {
                        layout = new android.widget.LinearLayout(activity);
                        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        int p = Utils.dipToPixels(10);
                        layout.setPadding(p, p, p, p);
                        android.graphics.drawable.GradientDrawable rowBg = new android.graphics.drawable.GradientDrawable();
                        rowBg.setColor(android.graphics.Color.argb(18, 255, 255, 255));
                        rowBg.setCornerRadius(Utils.dipToPixels(16));
                        rowBg.setStroke(Utils.dipToPixels(1), android.graphics.Color.argb(60, 255, 255, 255));
                        layout.setBackground(rowBg);

                        android.widget.TextView title = new android.widget.TextView(activity);
                        title.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                        title.setTextColor(DesignUtils.getPrimaryTextColor());
                        title.setTextSize(14f);
                        title.setTag("title");
                        layout.addView(title);
                        
                        android.widget.ImageView checkIcon = new android.widget.ImageView(activity);
                        checkIcon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Utils.dipToPixels(20), Utils.dipToPixels(20)));
                        checkIcon.setTag("check");
                        layout.addView(checkIcon);
                    } else {
                        layout = (android.widget.LinearLayout) convertView;
                    }

                    android.widget.TextView title = (android.widget.TextView) layout.findViewWithTag("title");
                    android.widget.ImageView checkIcon = (android.widget.ImageView) layout.findViewWithTag("check");
                    
                    boolean isSelected = (position == currentEffect);
                    title.setText(getEffectName(position));
                    title.setTypeface(isSelected ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                    title.setTextColor(isSelected ? android.graphics.Color.parseColor("#26A69A") : DesignUtils.getPrimaryTextColor());
                    
                    if (isSelected) {
                        try {
                            android.graphics.drawable.Drawable d = DesignUtils.getDrawableByName("ic_check");
                            if (d == null) d = activity.getDrawable(android.R.drawable.checkbox_on_background);
                            if (d != null) {
                                d.setTint(android.graphics.Color.parseColor("#26A69A"));
                                checkIcon.setImageDrawable(d);
                            }
                        } catch (Exception ignored) {}
                        checkIcon.setVisibility(android.view.View.VISIBLE);
                    } else {
                        checkIcon.setVisibility(android.view.View.GONE);
                    }
                    
                    return layout;
                }
            });
            
            listView.setOnItemClickListener((parent, view, position, id) -> {
                setEffect(position);
                Utils.showToast(String.format(activity.getString(ResId.string.voice_effect_s), getEffectName(position)), Toast.LENGTH_SHORT);
                if (menuItem != null) {
                    menuItem.setTitle(String.format(activity.getString(ResId.string.voice_prefix), getEffectName(position)));
                }
                dialog.dismiss();
            });

            android.widget.LinearLayout.LayoutParams listLp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dipToPixels(300));
            listLp.topMargin = p / 4;
            root.addView(listView, listLp);

            dialog.setView(root);
            dialog.setNegativeButton(activity.getString(ResId.string.cancel), null);
            dialog.show();
        } catch (Throwable e) {
            log("Error showing voice changer dialog: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    /**
     * Hook the OpusRecorder class to intercept voice recordings
     */
    private void hookOpusRecorder() throws Exception {
        // Find the OpusRecorder class
        Class<?> opusRecorderClass = Unobfuscator.loadOpusRecorderClass(classLoader);

        if (opusRecorderClass == null) {
            log("Could not find OpusRecorder class");
            return;
        }

        log("Found OpusRecorder class: " + opusRecorderClass.getName());

        // Log all methods in the class for debugging
        log("OpusRecorder methods:");
        for (Method m : opusRecorderClass.getDeclaredMethods()) {
            log("  - " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ") -> "
                    + m.getReturnType().getSimpleName());
        }

        // Hook the constructor to capture the file path
        // Constructor signature: OpusRecorder(String filePath,
        // PttNativeMetricsCallback, OpusRecorderConfig)
        XposedBridge.hookAllConstructors(opusRecorderClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                log("OpusRecorder constructor called with " + (param.args != null ? param.args.length : 0)
                        + " arguments");

                // Log all arguments for debugging
                if (param.args != null) {
                    for (int i = 0; i < param.args.length; i++) {
                        Object arg = param.args[i];
                        log("  arg[" + i + "]: " + (arg != null ? arg.getClass().getName() + " = " + arg : "null"));
                    }
                }

                // The first argument should be the file path
                if (param.args != null && param.args.length > 0 && param.args[0] instanceof String) {
                    currentRecordingPath = (String) param.args[0];
                    recordingProcessed = false;
                    log("OpusRecorder created with path: " + currentRecordingPath);

                }
            }
        });

        // Hook ALL methods that could be "stop" or end the recording
        // Try different method names that might stop recording
        String[] stopMethodNames = { "stop", "stopRecording", "finish", "close", "release" };
        boolean hookedAtLeastOne = false;

        for (String methodName : stopMethodNames) {
            for (Method method : opusRecorderClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName) ||
                        method.getName().toLowerCase().contains("stop")) {
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                log("OpusRecorder." + method.getName() + "() called");

                                if (currentRecordingPath == null) {
                                    log("No recording path captured, skipping processing");
                                    return;
                                }

                                if (recordingProcessed) {
                                    log("Recording already processed, skipping");
                                    return;
                                }

                                if (!nativeIsEnabled()) {
                                    log("Voice effect is disabled (effect=0), skipping processing");
                                    return;
                                }

                                final String filePath = currentRecordingPath;
                                recordingProcessed = true;

                                log("Recording stopped, will process: " + filePath);

                                // Process synchronously to ensure it's done before WhatsApp reads the file
                                try {
                                    processVoiceRecording(filePath);
                                } catch (Exception e) {
                                    log("Error processing voice recording: " + e.getMessage());
                                    XposedBridge.log(e);
                                }
                            }
                        });
                        log("Hooked method: " + method.getName());
                        hookedAtLeastOne = true;
                    } catch (Exception e) {
                        log("Failed to hook " + methodName + ": " + e.getMessage());
                    }
                }
            }
        }

        if (!hookedAtLeastOne) {
            // Fallback: hook all methods and log them to find the right one
            log("Could not find stop method, hooking all methods for debugging");
            for (Method method : opusRecorderClass.getDeclaredMethods()) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        log("OpusRecorder." + method.getName() + "() was called");
                    }
                });
            }
        }

        log("OpusRecorder hooks installed successfully");
    }

    /**
     * Process the voice recording with the selected effect
     */
    private void processVoiceRecording(String opusFilePath) throws Exception {
        File opusFile = new File(opusFilePath);
        if (!opusFile.exists()) {
            log("Opus file not found: " + opusFilePath);
            return;
        }

        log("Processing voice recording: " + opusFilePath);
        log("File size: " + opusFile.length() + " bytes");

        // Step 1: Decode opus to PCM
        AudioData audioData = decodeOpusToPcm(opusFile);
        if (audioData == null || audioData.pcmData == null || audioData.pcmData.length == 0) {
            log("Failed to decode opus file");
            return;
        }
        short[] pcmData = audioData.pcmData;
        int sampleRate = audioData.sampleRate;
        log("Decoded " + pcmData.length + " PCM samples at " + sampleRate + "Hz");

        // Step 2: Apply voice effect via native library
        short[] processedPcm = nativeProcessAudio(pcmData, sampleRate);
        if (processedPcm == null || processedPcm.length == 0) {
            log("Voice processing returned empty result");
            return;
        }
        log("Processed " + processedPcm.length + " PCM samples");

        // Step 3: Encode PCM back to opus
        File tempFile = new File(opusFile.getParent(), "voice_processed_temp.opus");
        boolean encoded = encodePcmToOpus(processedPcm, 48000, tempFile);
        if (!encoded) {
            log("Failed to encode processed audio");
            return;
        }
        log("Encoded to temp file: " + tempFile.getAbsolutePath() + " (" + tempFile.length() + " bytes)");

        // Step 4: Replace original file with processed file
        if (opusFile.delete() && tempFile.renameTo(opusFile)) {
            log("Voice recording processed successfully! Replaced original file.");
        } else {
            log("Failed to replace original file");
            // Try alternative: copy content
            try {
                copyFile(tempFile, opusFile);
                tempFile.delete();
                log("Voice recording processed successfully (via copy)");
            } catch (Exception e) {
                log("Failed to copy processed file: " + e.getMessage());
                tempFile.delete();
            }
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
                FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    public static class AudioData {
        public short[] pcmData;
        public int sampleRate;
        public AudioData(short[] pcmData, int sampleRate) {
            this.pcmData = pcmData;
            this.sampleRate = sampleRate;
        }
    }

    /**
     * Decode an audio file to PCM samples using MediaCodec
     */
    public static AudioData decodeOpusToPcm(File opusFile) {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(opusFile.getAbsolutePath());

            // Find the audio track
            int audioTrackIndex = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                XposedBridge.log("WaEnhancer: Track " + i + " mime: " + mime);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = trackFormat;
                    break;
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                XposedBridge.log("WaEnhancer: No audio track found in opus file");
                return null;
            }

            XposedBridge.log("WaEnhancer: Found audio track: " + format.toString());
            extractor.selectTrack(audioTrackIndex);

            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            
            int actualSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            
            // We'll target 48000Hz mono for perfect compatibility with WhatsApp VN
            final int TARGET_SAMPLE_RATE = 48000;

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            // Collect decoded samples
            java.util.ArrayList<Short> samples = new java.util.ArrayList<>();
            boolean inputDone = false;
            boolean outputDone = false;
            long timeoutUs = 10000; // 10ms timeout

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize,
                                    presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }

                // Get output
                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }

                    if (bufferInfo.size > 0) {
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        ShortBuffer shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();
                        int samplesToRead = shortBuffer.remaining();
                        if (samplesToRead > 0) {
                            if (channelCount == 1) {
                                while (shortBuffer.hasRemaining()) {
                                    samples.add(shortBuffer.get());
                                }
                            } else {
                                // Downmix to mono
                                while (shortBuffer.remaining() >= channelCount) {
                                    int sum = 0;
                                    for (int c = 0; c < channelCount; c++) {
                                        sum += shortBuffer.get();
                                    }
                                    samples.add((short) (sum / channelCount));
                                }
                            }
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = decoder.getOutputBuffers();
                }
            }

            // Convert ArrayList to short[] and resample to 48000 if needed
            short[] decodedMono = new short[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                decodedMono[i] = samples.get(i);
            }
            
            if (actualSampleRate == TARGET_SAMPLE_RATE) {
                return new AudioData(decodedMono, TARGET_SAMPLE_RATE);
            } else {
                // Linear Resampling to 48000Hz
                int newLength = (int) ((long) decodedMono.length * TARGET_SAMPLE_RATE / actualSampleRate);
                short[] resampled = new short[newLength];
                for (int i = 0; i < newLength; i++) {
                    float oldIdx = (float) i * actualSampleRate / TARGET_SAMPLE_RATE;
                    int index1 = (int) oldIdx;
                    int index2 = Math.min(index1 + 1, decodedMono.length - 1);
                    float weight = oldIdx - index1;
                    resampled[i] = (short) ((1.0f - weight) * decodedMono[index1] + weight * decodedMono[index2]);
                }
                return new AudioData(resampled, TARGET_SAMPLE_RATE);
            }

        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: Error decoding opus: " + e.getMessage());
            XposedBridge.log(e);
            return null;
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception ignored) {
                }
            }
            if (extractor != null) {
                extractor.release();
            }
        }
    }

    /**
     * Encode PCM samples to Opus file using MediaCodec
     */
    public static boolean encodePcmToOpus(short[] pcmData, int sampleRate, File outputFile) {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;

        try {
            // Create encoder for Opus
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000); // 64 kbps
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            if (pcmData != null) {
                long durationUs = (long) pcmData.length * 1000000L / sampleRate;
                format.setLong(MediaFormat.KEY_DURATION, durationUs);
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
            int trackIndex = -1;
            boolean muxerStarted = false;

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            // Convert short[] to byte[]
            byte[] pcmBytes = new byte[pcmData.length * 2];
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.nativeOrder())
                    .asShortBuffer().put(pcmData);

            int inputOffset = 0;
            boolean inputDone = false;
            boolean outputDone = false;
            long presentationTimeUs = 0;
            long timeoutUs = 10000;

            // Opus frame size is typically 960 samples for 48kHz (20ms)
            int frameSize = 960 * 2; // bytes per frame (mono, 16-bit)

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    int inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();

                            int bytesRemaining = pcmBytes.length - inputOffset;
                            int bytesToWrite = Math.min(Math.min(inputBuffer.capacity(), frameSize), bytesRemaining);

                            if (bytesToWrite > 0) {
                                inputBuffer.put(pcmBytes, inputOffset, bytesToWrite);
                                encoder.queueInputBuffer(inputBufferIndex, 0, bytesToWrite,
                                        presentationTimeUs, 0);
                                inputOffset += bytesToWrite;
                                presentationTimeUs += (bytesToWrite / 2) * 1000000L / sampleRate;
                            }

                            if (inputOffset >= pcmBytes.length) {
                                // Signal end of stream on next available buffer
                                int eosBufferIndex = encoder.dequeueInputBuffer(timeoutUs);
                                if (eosBufferIndex >= 0) {
                                    encoder.queueInputBuffer(eosBufferIndex, 0, 0,
                                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                }
                            }
                        }
                    }
                }

                // Get output
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }

                    if (bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            MediaFormat outputFormat = encoder.getOutputFormat();
                            trackIndex = muxer.addTrack(outputFormat);
                            muxer.start();
                            muxerStarted = true;
                        }

                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                        }
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        MediaFormat outputFormat = encoder.getOutputFormat();
                        trackIndex = muxer.addTrack(outputFormat);
                        muxer.start();
                        muxerStarted = true;
                    }
                }
            }

            // Verify file was written and has content
            return outputFile.exists() && outputFile.length() > 100;

        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: Error encoding opus: " + e.getMessage());
            XposedBridge.log(e);
            return false;
        } finally {
            if (encoder != null) {
                try {
                    encoder.stop();
                    encoder.release();
                } catch (Exception ignored) {
                }
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public String getPluginName() {
        return TAG;
    }
}
