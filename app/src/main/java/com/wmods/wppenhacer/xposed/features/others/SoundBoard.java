package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

// import com.wmods.wppenhacer.xposed.features.media.AttachmentAddons;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Soundboard Feature
 * Allows searching and sending sounds from MyInstants.com directly in
 * conversation.
 */
public class SoundBoard extends Feature {

    private static final String TAG = "SoundBoard";
    private static final int MENU_ITEM_ID = 0x503DB; // "SNDB" in hex-ish
    private final MyInstantsClient client = new MyInstantsClient();
    private MediaPlayer mediaPlayer;
    private final List<MyInstantsClient.SoundItem> currentList = new ArrayList<>();
    private SoundAdapter adapter;
    private ProgressBar progressBar;
    private String lastQuery = "";
    private String currentPlayingUrl = null;
    private TextView emptyState;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private int activeRequestId = 0;

    public SoundBoard(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("soundboard_enabled", true)) {
            return;
        }
        hookConversationMenu();
    }

    private void hookConversationMenu() throws Exception {
        var onCreateMenuConversationMethod = Unobfuscator.loadBlueOnReplayCreateMenuConversationMethod(classLoader);
        if (onCreateMenuConversationMethod == null) {
            log("Could not find conversation menu method");
            return;
        }
        log("Hooking menu method: " + onCreateMenuConversationMethod.getName());

        XposedBridge.hookMethod(onCreateMenuConversationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                addSoundBoardToParam(param);
            }
        });

        try {
            var onPrepareMethod = XposedHelpers.findMethodExact(onCreateMenuConversationMethod.getDeclaringClass(), "onPrepareOptionsMenu", Menu.class);
            XposedBridge.hookMethod(onPrepareMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    addSoundBoardToParam(param);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void addSoundBoardToParam(XC_MethodHook.MethodHookParam param) {
        var menu = (Menu) param.args[0];
        var activity = (Activity) param.thisObject;
        
        if (activity == null) {
            activity = WppCore.getCurrentConversation();
        }

        if (activity == null) {
            log("Menu hook triggered but activity is null");
            return;
        }
        addSoundBoardMenuItem(menu, activity);
    }

    private void addSoundBoardMenuItem(Menu menu, Activity activity) {
        if (menu.findItem(MENU_ITEM_ID) != null)
            return;

        String title = null;
        try {
            title = activity.getString(ResId.string.soundboard_title);
        } catch (Throwable e) {
            log("Failed to get title from ResId: " + e.getMessage());
        }
        
        if (title == null || title.isEmpty() || title.equals("0") || title.contains("ResId")) {
            title = "Soundboard";
        }

        log("Adding SoundBoard menu item to " + activity.getClass().getName() + " with title: " + title);
        MenuItem item = menu.add(0, MENU_ITEM_ID, 999, title);

        try {
            android.graphics.drawable.Drawable iconDraw = null;
            try {
                Context modContext = activity.createPackageContext("com.wmods.wppenhacer", Context.CONTEXT_IGNORE_SECURITY);
                int resId = modContext.getResources().getIdentifier("ic_soundboard_mixer", "drawable", "com.wmods.wppenhacer");
                if (resId != 0) {
                    iconDraw = modContext.getDrawable(resId);
                }
            } catch (Exception ignored) {
            }
            
            if (iconDraw == null) {
                iconDraw = DesignUtils.getDrawableByName("ic_volume_up");
                if (iconDraw == null) {
                    iconDraw = activity.getDrawable(android.R.drawable.ic_lock_silent_mode_off);
                }
            }
            if (iconDraw != null) {
                iconDraw.setTint(DesignUtils.getPrimaryTextColor());
                // Wrap in InsetDrawable with right inset to add spacing before text
                int rightInset = Utils.dipToPixels(6);
                android.graphics.drawable.InsetDrawable insetIcon =
                        new android.graphics.drawable.InsetDrawable(iconDraw, 0, 0, rightInset, 0);
                item.setIcon(insetIcon);
            }
        } catch (Throwable ignored) {
        }

        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        item.setOnMenuItemClickListener(mi -> {
            showSoundBoardDialog(activity);
            return true;
        });
    }

    private void showSoundBoardDialog(Activity activity) {
        try {
            AlertDialogWpp dialog = new AlertDialogWpp(activity);
            dialog.setTitle(activity.getString(ResId.string.soundboard_dialog_title));
            // dialog.setBlur(true);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            int p = Utils.dipToPixels(16);
            root.setPadding(p, p / 2, p, p);

            // Search Bar
            EditText searchBar = new EditText(activity);
            searchBar.setHint(activity.getString(ResId.string.soundboard_search_hint));
            searchBar.setSingleLine(true);
            searchBar.setPadding(p / 2, p / 2, p / 2, p / 2);
            searchBar.setTextColor(DesignUtils.getPrimaryTextColor());
            searchBar.setHintTextColor(Color.GRAY);
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setColor(Color.argb(25, 128, 128, 128));
            searchBg.setCornerRadius(Utils.dipToPixels(16));
            searchBg.setStroke(Utils.dipToPixels(1), Color.argb(45, 255, 255, 255));
            searchBar.setBackground(searchBg);
            root.addView(searchBar);

            // Progress Bar
            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.GONE);
            root.addView(progressBar);

            emptyState = new TextView(activity);
            emptyState.setTextColor(Color.GRAY);
            emptyState.setTextSize(12f);
            emptyState.setPadding(0, p / 3, 0, p / 3);
            emptyState.setVisibility(View.GONE);
            root.addView(emptyState);

            // List
            ListView listView = new ListView(activity);
            listView.setDivider(null);
            listView.setDividerHeight(Utils.dipToPixels(8));
            listView.setSelector(android.R.color.transparent);
            adapter = new SoundAdapter(activity, currentList);
            listView.setAdapter(adapter);

            LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dipToPixels(300));
            listLp.topMargin = p / 2;
            root.addView(listView, listLp);

            dialog.setView(root);
            dialog.setNegativeButton(activity.getString(ResId.string.cancel), (d, w) -> stopPlayback());
            dialog.create().setOnDismissListener(d -> stopPlayback());

            searchBar.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String query = s.toString().trim();
                    scheduleSearch(query);
                }
            });

            dialog.show();

            // Initial Load
            loadTrending();

        } catch (Throwable e) {
            log("Error showing soundboard dialog: " + e.getMessage());
        }
    }

    private void loadTrending() {
        final int requestId = ++activeRequestId;
        if (progressBar != null)
            progressBar.setVisibility(View.VISIBLE);
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setText("Loading trending sounds...");
        }
        client.getTrending(new MyInstantsClient.Callback<List<MyInstantsClient.SoundItem>>() {
            @Override
            public void onSuccess(List<MyInstantsClient.SoundItem> result) {
                updateList(result, requestId);
            }

            @Override
            public void onError(Exception e) {
                handleError(e, requestId);
            }
        });
    }

    private void performSearch(String query) {
        if (query.length() < 3 || query.equals(lastQuery))
            return;
        lastQuery = query;
        final int requestId = ++activeRequestId;
        if (progressBar != null)
            progressBar.setVisibility(View.VISIBLE);
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setText("Searching \"" + query + "\"...");
        }
        client.search(query, new MyInstantsClient.Callback<List<MyInstantsClient.SoundItem>>() {
            @Override
            public void onSuccess(List<MyInstantsClient.SoundItem> result) {
                updateList(result, requestId);
            }

            @Override
            public void onError(Exception e) {
                handleError(e, requestId);
            }
        });
    }

    private void scheduleSearch(String query) {
        if (pendingSearchRunnable != null) {
            uiHandler.removeCallbacks(pendingSearchRunnable);
        }
        pendingSearchRunnable = () -> {
            if (query.isEmpty()) {
                lastQuery = "";
                loadTrending();
            } else if (query.length() < 3) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (emptyState != null) {
                    emptyState.setVisibility(View.VISIBLE);
                    emptyState.setText("Type at least 3 characters to search");
                }
            } else {
                performSearch(query);
            }
        };
        uiHandler.postDelayed(pendingSearchRunnable, 420);
    }

    private void updateList(List<MyInstantsClient.SoundItem> items, int requestId) {
        Utils.getApplication().getMainExecutor().execute(() -> {
            if (requestId != activeRequestId) return;
            if (progressBar != null)
                progressBar.setVisibility(View.GONE);
            currentList.clear();
            currentList.addAll(items);
            if (adapter != null)
                adapter.notifyDataSetChanged();
            if (emptyState != null) {
                if (items.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    emptyState.setText("No sounds found");
                } else {
                    emptyState.setVisibility(View.GONE);
                }
            }
        });
    }

    private void handleError(Exception e, int requestId) {
        Utils.getApplication().getMainExecutor().execute(() -> {
            if (requestId != activeRequestId) return;
            if (progressBar != null)
                progressBar.setVisibility(View.GONE);
            // Search endpoints can occasionally return 404 during incomplete input.
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                currentList.clear();
                if (adapter != null) adapter.notifyDataSetChanged();
                if (emptyState != null) {
                    emptyState.setVisibility(View.VISIBLE);
                    emptyState.setText("No sounds found");
                }
                return;
            }
            Utils.showToast("Error: " + e.getMessage(), Toast.LENGTH_SHORT);
        });
    }

    private void playSound(String url) {
        if (url != null && url.equals(currentPlayingUrl) && mediaPlayer != null) {
            stopPlayback();
            return;
        }
        stopPlayback();
        currentPlayingUrl = url;
        if (adapter != null) adapter.notifyDataSetChanged();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> stopPlayback());
        } catch (IOException e) {
            currentPlayingUrl = null;
            if (adapter != null) adapter.notifyDataSetChanged();
            Utils.showToast(Utils.getApplication().getString(ResId.string.soundboard_play_error), Toast.LENGTH_SHORT);
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying())
                    mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        currentPlayingUrl = null;
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void sendSound(MyInstantsClient.SoundItem item) {
        Activity activity = WppCore.getCurrentConversation();
        if (activity == null) {
            Utils.showToast("No active conversation", Toast.LENGTH_SHORT);
            return;
        }

        // Get the current chat JID before going to background thread
        var jid = WppCore.getCurrentUserJid();
        if (jid == null || jid.isNull()) {
            Utils.showToast("Cannot determine current chat", Toast.LENGTH_SHORT);
            return;
        }
        String rawJid = jid.getPhoneRawString();

        Utils.showToast("Downloading sound...", Toast.LENGTH_SHORT);

        // Download MP3 on background thread, then send via targeted intent
        new Thread(() -> {
            try {
                // Download to public Downloads so WhatsApp can access the file
                File downloadDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), "WaEnhancer_Sounds");
                if (!downloadDir.exists())
                    downloadDir.mkdirs();

                String fileName = (item.slug != null ? item.slug : "sound") + ".mp3";
                File audioFile = new File(downloadDir, fileName);

                URL url = java.net.URI.create(item.mp3Url).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    conn.disconnect();
                    activity.runOnUiThread(() -> Utils.showToast("Sound unavailable (404)", Toast.LENGTH_SHORT));
                    return;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    activity.runOnUiThread(() -> Utils.showToast("Download failed: HTTP " + status, Toast.LENGTH_SHORT));
                    return;
                }

                try (InputStream in = conn.getInputStream();
                        FileOutputStream out = new FileOutputStream(audioFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                conn.disconnect();

                audioFile.setReadable(true, false);
                log("Downloaded sound to: " + audioFile.getAbsolutePath() + " (" + audioFile.length() + " bytes)");

                // Send via targeted Intent to WhatsApp, pre-filling the JID
                activity.runOnUiThread(() -> {
                    try {
                        android.os.StrictMode.VmPolicy oldPolicy = android.os.StrictMode.getVmPolicy();
                        try {
                            android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
                            // AttachmentAddons.markAudioNoteRequest();
                            Uri uri = Uri.fromFile(audioFile);
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("audio/mpeg");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                            shareIntent.putExtra("jid", rawJid);
                            shareIntent.putExtra("send_as_voice_note", true);
                            shareIntent.setPackage(activity.getPackageName());
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            activity.startActivity(shareIntent);
                        } finally {
                            android.os.StrictMode.setVmPolicy(oldPolicy);
                        }
                    } catch (Exception e) {
                        log("Share intent failed: " + e.getMessage());
                        Utils.showToast("Error sharing sound: " + e.getMessage(), Toast.LENGTH_SHORT);
                    }
                });

            } catch (Exception e) {
                log("Error downloading sound: " + e.getMessage());
                activity.runOnUiThread(() -> {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("404")) {
                        Utils.showToast("Sound unavailable (404)", Toast.LENGTH_SHORT);
                    } else {
                        Utils.showToast("Error downloading sound: " + msg, Toast.LENGTH_SHORT);
                    }
                });
            }
        }).start();
    }

    private class SoundAdapter extends BaseAdapter {
        private final Context context;
        private final List<MyInstantsClient.SoundItem> items;

        public SoundAdapter(Context context, List<MyInstantsClient.SoundItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout;
            if (convertView == null) {
                layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setGravity(Gravity.CENTER_VERTICAL);
                int p = Utils.dipToPixels(10);
                layout.setPadding(p, p, p, p);
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(Color.argb(18, 255, 255, 255));
                rowBg.setCornerRadius(Utils.dipToPixels(16));
                rowBg.setStroke(Utils.dipToPixels(1), Color.argb(60, 255, 255, 255));
                layout.setBackground(rowBg);

                TextView title = new TextView(context);
                title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                title.setTextColor(DesignUtils.getPrimaryTextColor());
                title.setTextSize(14);
                title.setTag("title");
                layout.addView(title);

                int btnHeight = Utils.dipToPixels(28);
                int btnPadding = Utils.dipToPixels(10);

                Button playBtn = new Button(context);
                playBtn.setText("PLAY");
                playBtn.setTextSize(10f);
                playBtn.setAllCaps(true);
                playBtn.setTextColor(Color.WHITE);
                playBtn.setPadding(btnPadding, 0, btnPadding, 0);
                playBtn.setMinHeight(0);
                playBtn.setMinimumHeight(0);
                playBtn.setMinWidth(0);
                playBtn.setMinimumWidth(0);

                GradientDrawable playBg = new GradientDrawable();
                playBg.setColor(Color.parseColor("#2E86DE"));
                playBg.setCornerRadius(Utils.dipToPixels(8));
                playBtn.setBackground(playBg);

                LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, btnHeight);
                playLp.rightMargin = Utils.dipToPixels(6);
                playBtn.setLayoutParams(playLp);
                playBtn.setTag("play");
                layout.addView(playBtn);

                Button sendBtn = new Button(context);
                sendBtn.setText("SEND");
                sendBtn.setTextSize(10f);
                sendBtn.setAllCaps(true);
                sendBtn.setTextColor(Color.WHITE);
                sendBtn.setPadding(btnPadding, 0, btnPadding, 0);
                sendBtn.setMinHeight(0);
                sendBtn.setMinimumHeight(0);
                sendBtn.setMinWidth(0);
                sendBtn.setMinimumWidth(0);

                GradientDrawable sendBg = new GradientDrawable();
                sendBg.setColor(Color.parseColor("#E67E22"));
                sendBg.setCornerRadius(Utils.dipToPixels(8));
                sendBtn.setBackground(sendBg);

                LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, btnHeight);
                sendBtn.setLayoutParams(sendLp);
                sendBtn.setTag("send");
                layout.addView(sendBtn);
            } else {
                layout = (LinearLayout) convertView;
            }

            MyInstantsClient.SoundItem item = items.get(position);
            TextView title = (TextView) layout.findViewWithTag("title");
            Button playBtn = (Button) layout.findViewWithTag("play");
            Button sendBtn = (Button) layout.findViewWithTag("send");
            boolean isPlaying = item.mp3Url != null && item.mp3Url.equals(currentPlayingUrl);

            title.setText(item.title);
            title.setTypeface(isPlaying ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            title.setTextColor(isPlaying ? Color.parseColor("#26A69A") : DesignUtils.getPrimaryTextColor());
            playBtn.setText(isPlaying ? "STOP" : "PLAY");
            GradientDrawable playBg = new GradientDrawable();
            playBg.setColor(isPlaying ? Color.parseColor("#C0392B") : Color.parseColor("#2E86DE"));
            playBg.setCornerRadius(Utils.dipToPixels(8));
            playBtn.setBackground(playBg);
            playBtn.setOnClickListener(v -> playSound(item.mp3Url));
            sendBtn.setOnClickListener(v -> {
                stopPlayback();
                sendSound(item);
            });

            return layout;
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Soundboard";
    }
}
