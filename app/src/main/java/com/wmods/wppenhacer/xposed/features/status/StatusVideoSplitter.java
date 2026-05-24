package com.wmods.wppenhacer.xposed.features.status;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class StatusVideoSplitter extends Feature {

    private static final int DEFAULT_SPLIT_DURATION = 30; // seconds
    // Track URIs that are already split segments to avoid re-triggering the split
    // dialog
    private static final Set<String> splitSegmentUris = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Static fields to hold remaining segments between activity recreations
    private static ArrayList<Uri> pendingSegments = null;
    private static Intent pendingIntent = null;
    // Flag to distinguish our own activity restarts from user finishing posting
    private static boolean isRelaunchingWithSegment = false;
    // Track total segments for toast messages
    private static int totalSegmentCount = 0;
    private static int currentSegmentIndex = 0;
    // Caption from the first segment to carry over to subsequent segments
    private static String pendingCaption = null;
    // Track all segment URIs for auto-deletion after posting
    private static ArrayList<Uri> allSegmentUris = null;

    public StatusVideoSplitter(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("status_video_splitter", false)) {
            return;
        }

        // Hook MediaComposerActivity to intercept when video is selected for status
        try {
            // Try new package structure first (WhatsApp 2.26+)
            Class<?> mediaComposerClass = XposedHelpers.findClassIfExists(
                    "com.whatsapp.mediacomposer.ui.app.MediaComposerActivity", classLoader);

            // Fallback to old package
            if (mediaComposerClass == null) {
                mediaComposerClass = XposedHelpers.findClassIfExists(
                        "com.whatsapp.mediacomposer.MediaComposerActivity", classLoader);
            }

            if (mediaComposerClass != null) {
                log("[StatusVideoSplitter] Found MediaComposerActivity: " + mediaComposerClass.getName());

                // Hook onCreate to check if it's a status video
                XposedBridge.hookAllMethods(mediaComposerClass, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        Intent intent = activity.getIntent();

                        log("[StatusVideoSplitter] MediaComposerActivity created");

                        // Check if this is for status
                        String jid = intent.getStringExtra("jid");
                        log("[StatusVideoSplitter] JID: " + jid);

                        if (jid != null && jid.equals("status@broadcast")) {
                            log("[StatusVideoSplitter] Status detected");

                            // Get media URIs - try multiple intent extra keys WhatsApp uses
                            Uri videoUri = null;

                            // First check if there's a data URI
                            if (intent.getData() != null) {
                                videoUri = intent.getData();
                                log("[StatusVideoSplitter] Found data URI: " + videoUri);
                            }

                            // Try EXTRA_STREAM
                            if (videoUri == null) {
                                ArrayList<Uri> mediaUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                                if (mediaUris != null && mediaUris.size() == 1) {
                                    videoUri = mediaUris.get(0);
                                } else if (mediaUris == null) {
                                    Uri singleUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                                    if (singleUri != null) {
                                        videoUri = singleUri;
                                    }
                                } else {
                                    log("[StatusVideoSplitter] Multiple URIs (" + mediaUris.size() + "), skipping");
                                    return;
                                }
                            }

                            if (videoUri == null) {
                                log("[StatusVideoSplitter] No video URI found");
                                return;
                            }

                            // Skip if this URI is from a split segment (prevent re-trigger)
                            if (splitSegmentUris.contains(videoUri.toString())) {
                                log("[StatusVideoSplitter] Loading split segment: " + videoUri
                                        + " (part " + currentSegmentIndex + "/" + totalSegmentCount + ")");
                                // Show a toast so the user knows which part they're posting
                                if (currentSegmentIndex > 1) {
                                    Utils.showToast("Part " + currentSegmentIndex + " of " + totalSegmentCount, Toast.LENGTH_SHORT);
                                }

                                // Pre-fill caption from the first segment if available
                                if (pendingCaption != null && !pendingCaption.isEmpty()) {
                                    log("[StatusVideoSplitter] Will set caption: " + pendingCaption);
                                    final String caption = pendingCaption;
                                    // Delay to let the activity fully initialize its views
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            ArrayList<EditText> editTexts = new ArrayList<>();
                                            findAllEditTexts(activity.getWindow().getDecorView(), editTexts);
                                            for (EditText et : editTexts) {
                                                if (et.getVisibility() == View.VISIBLE && et.isEnabled()) {
                                                    et.setText(caption);
                                                    et.setSelection(caption.length());
                                                    log("[StatusVideoSplitter] Caption set on: "
                                                            + et.getClass().getName());
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            log("[StatusVideoSplitter] Failed to set caption: " + e.getMessage());
                                        }
                                    }, 1500);
                                }
                                return;
                            }

                            log("[StatusVideoSplitter] Single media URI: " + videoUri);

                            if (isVideoFile(activity, videoUri)) {
                                long duration = getVideoDuration(activity, videoUri);
                                log("[StatusVideoSplitter] Video duration: " + duration + "ms");

                                // Only show split dialog if video is longer than 30 seconds
                                if (duration > 30000) {
                                    log("[StatusVideoSplitter] Long video detected, showing dialog");
                                    showSplitDialog(activity, videoUri, intent);
                                } else {
                                    log("[StatusVideoSplitter] Video too short, skipping");
                                }
                            } else {
                                log("[StatusVideoSplitter] Not a video file");
                            }
                        }
                    }
                });

                // Hook onPause to capture caption while views are still alive
                // (onDestroy is too late - views are already detached)
                XposedBridge.hookAllMethods(mediaComposerClass, "onPause", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Only capture caption when we're in split segment mode
                        if (totalSegmentCount > 0) {
                            Activity activity = (Activity) param.thisObject;
                            try {
                                String caption = captureCaption(activity);
                                if (caption != null && !caption.isEmpty()) {
                                    pendingCaption = caption;
                                    log("[StatusVideoSplitter] onPause: Captured caption: " + caption);
                                } else {
                                    log("[StatusVideoSplitter] onPause: No caption found (empty or null)");
                                }
                            } catch (Exception e) {
                                log("[StatusVideoSplitter] onPause: Failed to capture caption: " + e.getMessage());
                            }
                        }
                    }
                });

                // Hook onDestroy to auto-launch the next segment after user posts one
                XposedBridge.hookAllMethods(mediaComposerClass, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // If we ourselves are causing this destroy (relaunch with first segment),
                        // don't auto-launch the next one
                        if (isRelaunchingWithSegment) {
                            log("[StatusVideoSplitter] onDestroy: skipping (we are relaunching)");
                            isRelaunchingWithSegment = false;
                            return;
                        }

                        // Check if there are pending segments to post
                        if (pendingSegments != null && !pendingSegments.isEmpty()) {
                            Uri nextSegment = pendingSegments.remove(0);
                            currentSegmentIndex++;
                            int remaining = pendingSegments.size();

                            log("[StatusVideoSplitter] onDestroy: auto-launching next segment "
                                    + currentSegmentIndex + "/" + totalSegmentCount
                                    + " (" + remaining + " remaining): " + nextSegment);

                            if (pendingSegments.isEmpty()) {
                                pendingSegments = null;
                            }

                            // Build the intent for the next segment
                            Intent newIntent = new Intent(pendingIntent);
                            newIntent.setData(nextSegment);
                            ArrayList<Uri> nextList = new ArrayList<>();
                            nextList.add(nextSegment);
                            newIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, nextList);
                            newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                            // Also pass caption via intent extra for compatibility
                            if (pendingCaption != null && !pendingCaption.isEmpty()) {
                                newIntent.putExtra(Intent.EXTRA_TEXT, pendingCaption);
                            }

                            Context appContext = ((Activity) param.thisObject).getApplicationContext();

                            // Small delay to let the current activity fully finish
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    Utils.showToast("Loading part " + currentSegmentIndex + " of " + totalSegmentCount + "...", Toast.LENGTH_SHORT);
                                    appContext.startActivity(newIntent);
                                } catch (Exception e) {
                                    log("[StatusVideoSplitter] Failed to auto-launch next segment: " + e.getMessage());
                                }
                            }, 800);
                        } else {
                            // All segments posted, clean up
                            if (totalSegmentCount > 0) {
                                Context appContext = ((Activity) param.thisObject).getApplicationContext();
                                int postedCount = totalSegmentCount;
                                Utils.showToast("All " + postedCount + " status parts posted! Split files will be cleaned up shortly.", Toast.LENGTH_LONG);

                                // Auto-delete split segment files from MediaStore after a delay
                                // (60s to ensure WhatsApp has finished uploading)
                                if (allSegmentUris != null && !allSegmentUris.isEmpty()) {
                                    ArrayList<Uri> urisToDelete = new ArrayList<>(allSegmentUris);
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        int deleted = 0;
                                        for (Uri uri : urisToDelete) {
                                            try {
                                                int rows = appContext.getContentResolver().delete(uri, null, null);
                                                if (rows > 0)
                                                    deleted++;
                                                log("[StatusVideoSplitter] Deleted segment from MediaStore: " + uri);
                                            } catch (Exception e) {
                                                log("[StatusVideoSplitter] Failed to delete segment: " + uri + " - "
                                                        + e.getMessage());
                                            }
                                        }
                                        log("[StatusVideoSplitter] Auto-deleted " + deleted + "/" + urisToDelete.size()
                                                + " split segments");
                                        Utils.showToast("Cleaned up " + deleted + " split video files.", Toast.LENGTH_SHORT);
                                    }, 60000); // 60 second delay
                                }

                                // Reset state
                                totalSegmentCount = 0;
                                currentSegmentIndex = 0;
                                pendingIntent = null;
                                pendingCaption = null;
                                allSegmentUris = null;
                                splitSegmentUris.clear();
                            }
                        }
                    }
                });

                log("[StatusVideoSplitter] Hooked MediaComposerActivity successfully");
            } else {
                log("[StatusVideoSplitter] ERROR: MediaComposerActivity class not found!");
            }
        } catch (Exception e) {
            log("[StatusVideoSplitter] Failed to hook MediaComposerActivity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isVideoFile(Context context, Uri uri) {
        try {
            String type = context.getContentResolver().getType(uri);
            return type != null && type.startsWith("video/");
        } catch (Exception e) {
            return false;
        }
    }

    private long getVideoDuration(Context context, Uri videoUri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            return Long.parseLong(durationStr);
        } catch (Exception e) {
            return 0;
        }
    }

    private void showSplitDialog(Activity activity, Uri videoUri, Intent originalIntent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Create layout programmatically since we can't access WaEnhancer resources
        // from WhatsApp
        android.widget.LinearLayout layout = new android.widget.LinearLayout(activity);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText("Split into " + DEFAULT_SPLIT_DURATION + " second segments");
        tvLabel.setTextSize(16);
        tvLabel.setPadding(0, 0, 0, 20);
        layout.addView(tvLabel);

        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(5);
        seekBar.setProgress(1);
        layout.addView(seekBar);

        TextView tvScale = new TextView(activity);
        tvScale.setText("15s - 30s - 45s - 60s - 75s - 90s");
        tvScale.setTextSize(12);
        tvScale.setPadding(0, 10, 0, 0);
        layout.addView(tvScale);

        final int[] splitDuration = { DEFAULT_SPLIT_DURATION };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                splitDuration[0] = (progress + 1) * 15;
                tvLabel.setText("Split into " + splitDuration[0] + " second segments");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        builder.setView(layout)
                .setTitle("Video too long for status")
                .setMessage(
                        "This video is longer than 30 seconds. Would you like to split it into multiple status segments?")
                .setPositiveButton("Split Video", (dialog, which) -> {
                    splitVideo(activity, videoUri, splitDuration[0], originalIntent);
                })
                .setNegativeButton("Continue as is", (dialog, which) -> {
                    // Do nothing, let WhatsApp handle it normally
                })
                .setCancelable(false)
                .show();
    }

    private void splitVideo(Activity activity, Uri videoUri, int splitDurationSeconds, Intent originalIntent) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Splitting Video");
        progressDialog.setMessage("Please wait...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        executor.execute(() -> {
            try {
                ArrayList<Uri> segments = splitVideoIntoSegments(activity, videoUri, splitDurationSeconds,
                        progressDialog);

                handler.post(() -> {
                    progressDialog.dismiss();

                    if (segments.isEmpty()) {
                        Utils.showToast("Failed to split video", Toast.LENGTH_SHORT);
                    } else if (segments.size() == 1) {
                        Utils.showToast("Video is already short enough", Toast.LENGTH_SHORT);
                    } else {
                        // Mark all segment URIs so they won't re-trigger the split dialog
                        for (Uri segUri : segments) {
                            splitSegmentUris.add(segUri.toString());
                        }

                        // Track all segment URIs for auto-deletion after all parts posted
                        allSegmentUris = new ArrayList<>(segments);

                        // Set up the sequential posting state
                        totalSegmentCount = segments.size();
                        currentSegmentIndex = 1;

                        // Store remaining segments (all except the first)
                        pendingSegments = new ArrayList<>(segments.subList(1, segments.size()));
                        pendingIntent = new Intent(originalIntent);

                        // Load the first segment
                        Uri firstSegment = segments.get(0);
                        log("[StatusVideoSplitter] Loading first segment (1/" + totalSegmentCount + "): "
                                + firstSegment);

                        Intent newIntent = new Intent(originalIntent);
                        newIntent.setData(firstSegment);
                        ArrayList<Uri> firstList = new ArrayList<>();
                        firstList.add(firstSegment);
                        newIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, firstList);
                        newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        Utils.showToast("Split into " + totalSegmentCount + " parts. Loading part 1...", Toast.LENGTH_LONG);

                        // Set relaunch flag so onDestroy doesn't trigger next segment
                        isRelaunchingWithSegment = true;
                        activity.finish();
                        activity.startActivity(newIntent);
                    }
                });
            } catch (Exception e) {
                log("[StatusVideoSplitter] Error splitting video: " + e.getMessage());
                e.printStackTrace();
                handler.post(() -> {
                    progressDialog.dismiss();
                    Utils.showToast("Error: " + e.getMessage(), Toast.LENGTH_LONG);
                });
            }
        });
    }

    /**
     * Insert a video file into MediaStore so WhatsApp can read it via content://
     * URI
     */
    private Uri insertIntoMediaStore(Context context, File videoFile, int segmentNumber) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME,
                "status_split_" + segmentNumber + "_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StatusSplitter");

        Uri contentUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (contentUri == null) {
            throw new IOException("Failed to create MediaStore entry for segment " + segmentNumber);
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(contentUri);
                FileInputStream fis = new FileInputStream(videoFile)) {
            if (os == null) {
                throw new IOException("Failed to open output stream for segment " + segmentNumber);
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }

        log("[StatusVideoSplitter] Inserted segment " + segmentNumber + " into MediaStore: " + contentUri);
        return contentUri;
    }

    private ArrayList<Uri> splitVideoIntoSegments(Context context, Uri videoUri, int splitDurationSeconds,
            ProgressDialog progressDialog) throws IOException {
        ArrayList<Uri> segments = new ArrayList<>();
        MediaExtractor extractor = null;

        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long totalDurationMs = Long.parseLong(durationStr);
            retriever.release();

            long splitDurationMs = splitDurationSeconds * 1000L;

            if (totalDurationMs <= splitDurationMs) {
                segments.add(videoUri);
                return segments;
            }

            // Use internal cache for temp files, then insert into MediaStore
            File outputDir = new File(context.getCacheDir(), "split_videos");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            int numSegments = (int) Math.ceil((double) totalDurationMs / splitDurationMs);
            progressDialog.setMax(numSegments);

            extractor = new MediaExtractor();
            extractor.setDataSource(context, videoUri, null);

            int videoTrackIndex = -1;
            int audioTrackIndex = -1;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString("mime");
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i;
                } else if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                }
            }

            if (videoTrackIndex == -1) {
                throw new IOException("No video track found");
            }

            for (int i = 0; i < numSegments; i++) {
                long startTimeUs = i * splitDurationMs * 1000;
                long endTimeUs = Math.min((i + 1) * splitDurationMs * 1000, totalDurationMs * 1000);

                File outputFile = new File(outputDir, "segment_" + (i + 1) + ".mp4");

                // Delete existing file
                if (outputFile.exists()) {
                    outputFile.delete();
                }

                MediaMuxer muxer = null;
                try {
                    muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                    int muxerVideoTrack = muxer.addTrack(extractor.getTrackFormat(videoTrackIndex));
                    int muxerAudioTrack = -1;
                    if (audioTrackIndex != -1) {
                        muxerAudioTrack = muxer.addTrack(extractor.getTrackFormat(audioTrackIndex));
                    }

                    muxer.start();

                    // Process video
                    extractor.selectTrack(videoTrackIndex);
                    extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

                    ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                    android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();

                    while (true) {
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            break;
                        }

                        long sampleTime = extractor.getSampleTime();
                        if (sampleTime > endTimeUs) {
                            break;
                        }

                        if (extractor.getSampleTrackIndex() == videoTrackIndex) {
                            bufferInfo.offset = 0;
                            bufferInfo.size = sampleSize;
                            bufferInfo.presentationTimeUs = sampleTime - startTimeUs;
                            bufferInfo.flags = extractor.getSampleFlags();
                            muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo);
                        }

                        extractor.advance();
                    }

                    extractor.unselectTrack(videoTrackIndex);

                    // Process audio
                    if (audioTrackIndex != -1) {
                        extractor.selectTrack(audioTrackIndex);
                        extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

                        while (true) {
                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) {
                                break;
                            }

                            long sampleTime = extractor.getSampleTime();
                            if (sampleTime > endTimeUs) {
                                break;
                            }

                            if (extractor.getSampleTrackIndex() == audioTrackIndex) {
                                bufferInfo.offset = 0;
                                bufferInfo.size = sampleSize;
                                bufferInfo.presentationTimeUs = sampleTime - startTimeUs;
                                bufferInfo.flags = extractor.getSampleFlags();
                                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo);
                            }

                            extractor.advance();
                        }

                        extractor.unselectTrack(audioTrackIndex);
                    }

                    muxer.stop();

                    // Verify the file was created and has content
                    if (outputFile.exists() && outputFile.length() > 0) {
                        // Insert into MediaStore to get a content:// URI
                        Uri contentUri = insertIntoMediaStore(context, outputFile, i + 1);
                        segments.add(contentUri);
                        log("[StatusVideoSplitter] Created segment " + (i + 1) + ": " + contentUri
                                + " (" + outputFile.length() + " bytes)");

                        // Delete temp file after inserting into MediaStore
                        outputFile.delete();
                    } else {
                        throw new IOException("Failed to create segment " + (i + 1));
                    }

                } finally {
                    if (muxer != null) {
                        try {
                            muxer.release();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }

                progressDialog.setProgress(i + 1);
            }
        } finally {
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return segments;
    }

    /**
     * Capture the caption text from the activity's view hierarchy.
     * Scans all EditText widgets to find one with user-entered text.
     */
    private static String captureCaption(Activity activity) {
        try {
            View rootView = activity.getWindow().getDecorView();
            ArrayList<EditText> allEditTexts = new ArrayList<>();
            findAllEditTexts(rootView, allEditTexts);
            XposedBridge.log("[StatusVideoSplitter] captureCaption: found " + allEditTexts.size() + " EditText(s)");

            for (EditText et : allEditTexts) {
                String text = et.getText().toString().trim();
                String className = et.getClass().getName();
                String hint = et.getHint() != null ? et.getHint().toString() : "(no hint)";
                XposedBridge.log("[StatusVideoSplitter] captureCaption: EditText class=" + className
                        + " hint=" + hint
                        + " text=\"" + text + "\""
                        + " visible=" + (et.getVisibility() == View.VISIBLE));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (Exception e) {
            XposedBridge.log("[StatusVideoSplitter] captureCaption error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Recursively collect all EditText widgets in the view hierarchy.
     * This catches standard EditText as well as custom subclasses.
     */
    private static void findAllEditTexts(View view, ArrayList<EditText> results) {
        if (view instanceof EditText) {
            results.add((EditText) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAllEditTexts(group.getChildAt(i), results);
            }
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Status Video Splitter";
    }
}
