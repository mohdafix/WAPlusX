package com.wmods.wppenhacer.xposed.features.others;

import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import org.luckypray.dexkit.query.enums.StringMatchType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.content.res.XModuleResources;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.WppXposed;

public class GoogleTranslate extends Feature {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public GoogleTranslate(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("google_translate", false)) {
            return;
        }

        Method checkSupportLanguage;
        try {
            checkSupportLanguage = Unobfuscator.loadCheckSupportLanguage(classLoader);
        } catch (Exception e) {
            logDebug("Failed to load checkSupportLanguage: " + e.getMessage());
            return;
        }

        if (checkSupportLanguage == null) {
            logDebug("checkSupportLanguage method not found via Unobfuscator.");
            return;
        }

        XposedBridge.hookMethod(checkSupportLanguage, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // Bypass language support check by spoofing a known supported pair
                param.args[0] = "pt";
                param.args[1] = "en";
            }
        });

        Class<?> translatorClazz = checkSupportLanguage.getDeclaringClass();

        // Dynamically search for translation methods in this class and related
        // translation components
        Method stringTranslate = null;
        Method listTranslate = null;

        List<Class<?>> classesToSearch = new ArrayList<>();
        classesToSearch.add(translatorClazz);

        Class<?> unityTranslator = XposedHelpers
                .findClassIfExists("com.whatsapp.messagetranslation.UnityMessageTranslation", classLoader);
        if (unityTranslator != null) {
            classesToSearch.add(unityTranslator);
        }

        Class<?> mlProcessor = XposedHelpers.findClassIfExists("com.whatsapp.messagetranslation.TranslationMLProcessor",
                classLoader);
        if (mlProcessor != null) {
            classesToSearch.add(mlProcessor);
        }

        for (Class<?> clazz : classesToSearch) {
            for (Method m : clazz.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                Class<?> returnType = m.getReturnType();
                if (returnType == void.class || returnType == Object.class)
                    continue;

                boolean isResultClass = false;
                try {
                    // Check for constructors like (String/String[], float, int)
                    for (var ctor : returnType.getDeclaredConstructors()) {
                        Class<?>[] cParams = ctor.getParameterTypes();
                        if (cParams.length >= 3 &&
                                (cParams[0] == String.class || cParams[0] == String[].class) &&
                                cParams[1] == float.class &&
                                cParams[2] == int.class) {
                            isResultClass = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }

                if (isResultClass) {
                    if (params.length == 1 && params[0] == String.class) {
                        stringTranslate = m;
                    } else if (params.length == 1 && params[0] == List.class) {
                        listTranslate = m;
                    }
                }

                if (stringTranslate != null && listTranslate != null)
                    break;
            }
            if (stringTranslate != null && listTranslate != null)
                break;
        }

        if (stringTranslate != null) {
            final Class<?> targetClazz = stringTranslate.getDeclaringClass();
            XposedHelpers.findAndHookMethod(targetClazz, stringTranslate.getName(), String.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String texto = (String) param.args[0];
                            logDebug("Translating string: "
                                    + (texto.length() > 20 ? texto.substring(0, 20) + "..." : texto));

                            String translation = getTranslation(texto);
                            Class<?> returnType = ((Method) param.method).getReturnType();

                            // Call the identified constructor
                            for (var ctor : returnType.getDeclaredConstructors()) {
                                Class<?>[] cParams = ctor.getParameterTypes();
                                if (cParams.length >= 3 && cParams[0] == String.class && cParams[1] == float.class
                                        && cParams[2] == int.class) {
                                    ctor.setAccessible(true);
                                    if (cParams.length == 3)
                                        return ctor.newInstance(translation, 1.0f, 0);
                                    if (cParams.length == 5)
                                        return ctor.newInstance(translation, 1.0f, 0, 0, null);
                                }
                            }
                            return null;
                        }
                    });
        }

        if (listTranslate != null) {
            final Class<?> targetClazz = listTranslate.getDeclaringClass();
            XposedHelpers.findAndHookMethod(targetClazz, listTranslate.getName(), List.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            List<?> list = (List<?>) param.args[0];
                            ArrayList<String> translated = new ArrayList<>();
                            for (Object obj : list) {
                                if (obj instanceof String) {
                                    translated.add(getTranslation((String) obj));
                                }
                            }

                            Class<?> returnType = ((Method) param.method).getReturnType();
                            String[] translatedArray = translated.toArray(new String[0]);

                            for (var ctor : returnType.getDeclaredConstructors()) {
                                Class<?>[] cParams = ctor.getParameterTypes();
                                if (cParams.length >= 3 && cParams[0] == String[].class && cParams[1] == float.class
                                        && cParams[2] == int.class) {
                                    ctor.setAccessible(true);
                                    if (cParams.length == 3)
                                        return ctor.newInstance(translatedArray, 1.0f, 0);
                                    if (cParams.length == 5)
                                        return ctor.newInstance(translatedArray, 1.0f, 0, 0, null);
                                }
                            }
                            return null;
                        }
                    });
        }

        if (stringTranslate == null && listTranslate == null) {
            XposedBridge
                    .log("GoogleTranslate: ERROR - Could not find translation methods in " + translatorClazz.getName());
        }

        // Inject translate button into Chat Entry (Input Area)
        try {
            injectTranslateButtonToInput();
        } catch (Exception e) {
            logDebug("Failed to inject translate button: " + e.getMessage());
        }
    }

    private String getTranslation(String text) {
        if (TextUtils.isEmpty(text))
            return text;

        prefs.reload();
        String provider = prefs.getString("translation_provider", "google");
        String targetLang = prefs.getString("target_translation_language", "");
        if (TextUtils.isEmpty(targetLang)) {
            targetLang = Locale.getDefault().getLanguage();
        }

        try {
            return translateStatic(text, provider, targetLang).get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            XposedBridge.log("GoogleTranslate: Translation Error (" + provider + "): " + e.toString());
            return text;
        }
    }

    public static CompletableFuture<String> translateStatic(String text, String provider, String targetLang) {
        if ("groq".equals(provider)) {
            return translateGroq(text, targetLang, WppXposed.getPref());
        } else if ("gemini".equals(provider)) {
            return translateGemini(text, targetLang, WppXposed.getPref());
        } else {
            return translateGoogle(text, targetLang);
        }
    }

    public static CompletableFuture<String> translateGoogle(String text, String languageDest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String url;
        try {
            url = String.format(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s",
                    languageDest,
                    URLEncoder.encode(text, "UTF-8"));
        } catch (Exception e) {
            future.completeExceptionally(new RuntimeException("Error encoding URL: " + e.getMessage()));
            return future;
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(new RuntimeException("Error fetching translation: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    try {
                        JSONArray jsonArray = new JSONArray(responseData);
                        JSONArray translations = jsonArray.getJSONArray(0);
                        StringBuilder translation = new StringBuilder();

                        for (int i = 0; i < translations.length(); i++) {
                            JSONArray item = translations.getJSONArray(i);
                            translation.append(item.getString(0));
                        }

                        future.complete(translation.toString());
                    } catch (Exception e) {
                        future.completeExceptionally(
                                new RuntimeException("Error processing response: " + e.getMessage()));
                    }
                } else {
                    future.completeExceptionally(new RuntimeException("Response not successful: " + response.code()));
                }
            }
        });

        return future;
    }

    public static CompletableFuture<String> translateGroq(String text, String targetLang, XSharedPreferences prefs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        prefs.reload();
        String apiKey = prefs.getString("groq_api_key", "").trim();
        if (TextUtils.isEmpty(apiKey)) {
            future.complete(text + " [Groq API key not provided]");
            return future;
        }

        try {
            JSONObject root = new JSONObject();
            root.put("model", "llama-3.1-8b-instant");

            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a professional translator. Translate the following text to "
                    + targetLang + ". Provide ONLY the translated text without any explanations or extra characters.");
            messages.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", text);
            messages.put(userMessage);

            root.put("messages", messages);
            root.put("temperature", 0);

            Request request = new Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(root.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        if (!response.isSuccessful()) {
                            future.complete(text + " [Groq Error: " + response.code() + "]");
                            return;
                        }
                        JSONObject result = new JSONObject(body);
                        future.complete(result.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content").trim());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static CompletableFuture<String> translateGemini(String text, String targetLang, XSharedPreferences prefs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        prefs.reload();
        String apiKey = prefs.getString("gemini_api_key", "").trim();
        String modelName = prefs.getString("gemini_model", "gemini-1.5-flash").trim();

        if (TextUtils.isEmpty(apiKey)) {
            future.complete(text + " [Gemini API key not provided]");
            return future;
        }

        try {
            JSONObject root = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray partsArray = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("text", "Translate the following text to " + targetLang
                    + ". Provide ONLY the translated text.\n\nText: " + text);
            partsArray.put(textPart);

            contentObj.put("parts", partsArray);
            contentsArray.put(contentObj);
            root.put("contents", contentsArray);

            Request request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/" + modelName
                            + ":generateContent?key=" + apiKey)
                    .post(RequestBody.create(root.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        if (!response.isSuccessful()) {
                            future.complete(text + " [Gemini Error: " + response.code() + "]");
                            return;
                        }
                        JSONObject jsonResult = new JSONObject(body);
                        future.complete(jsonResult.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text").trim());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam, XModuleResources modRes) {
        // Translation button is now injected at runtime via doHook() - no resource hooks needed
    }

    /**
     * Injects the translate button into the WhatsApp Chat Entry (input area).
     * Hooks Conversation activity and searches for the 'entry' EditText.
     */
    private void injectTranslateButtonToInput() {
        try {
            var conversationClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "Conversation");
            if (conversationClass != null) {
                XposedHelpers.findAndHookMethod(conversationClass, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.app.Activity activity = (android.app.Activity) param.thisObject;
                        
                        // Use a handler to ensure layout is fully ready
                        activity.getWindow().getDecorView().post(() -> {
                            try {
                                int entryId = Utils.getID("entry", "id");
                                if (entryId == -1) return;

                                View entry = activity.findViewById(entryId);
                                if (!(entry instanceof EditText)) return;

                                ViewGroup parent = (ViewGroup) entry.getParent();
                                if (parent == null) return;

                                // Check if already injected
                                if (parent.findViewWithTag("wae_translate_btn") != null) return;

                                ImageButton btn = new ImageButton(activity);
                                btn.setTag("wae_translate_btn");
                                
                                // Use module's translate icon
                                if (ResId.drawable.ic_translator != 0) {
                                    android.graphics.drawable.Drawable icon = com.wmods.wppenhacer.xposed.utils.DesignUtils.getDrawable(ResId.drawable.ic_translator);
                                    if (icon != null) {
                                        icon.mutate().setTint(com.wmods.wppenhacer.xposed.utils.DesignUtils.getPrimaryTextColor());
                                        btn.setImageDrawable(icon);
                                    }
                                }

                                btn.setBackground(null);
                                
                                // Layout params: similar to emoji button
                                int size = Utils.dipToPixels(40f);
                                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                                lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                                btn.setLayoutParams(lp);

                                btn.setOnClickListener(v -> showTextToolsMenu(v, activity));
                                btn.setOnLongClickListener(v -> {
                                    onTranslateButtonClicked(activity);
                                    return true;
                                });

                                // Insert before the text entry field for visibility
                                int index = parent.indexOfChild(entry);
                                parent.addView(btn, index);
                                
                                XposedBridge.log("GoogleTranslate: Injected button into Chat Entry");
                            } catch (Throwable e) {
                                XposedBridge.log("GoogleTranslate: Injection error: " + e.getMessage());
                            }
                        });
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called when the translate button in the toolbar is clicked.
     * Finds the text input, translates the text, and replaces it.
     */
    private void onTranslateButtonClicked(android.app.Activity activity) {
        try {
            // Find the entry EditText (WhatsApp's message input)
            int entryId = Utils.getID("entry", "id");
            EditText entry = null;

            if (entryId != -1) {
                View v = activity.findViewById(entryId);
                if (v instanceof EditText) {
                    entry = (EditText) v;
                }
            }

            // Fallback: search for visible EditTexts
            if (entry == null) {
                View rootView = activity.getWindow().getDecorView();
                java.util.ArrayList<EditText> edits = new java.util.ArrayList<>();
                findEditTexts(rootView, edits);
                for (EditText e : edits) {
                    if (e.getVisibility() == View.VISIBLE && e.isEnabled()) {
                        entry = e;
                        break;
                    }
                }
            }

            if (entry == null) {
                Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
                return;
            }

            String textToTranslate = entry.getText().toString().trim();
            if (textToTranslate.isEmpty()) {
                Utils.showToast("Type a message to translate", Toast.LENGTH_SHORT);
                return;
            }

            prefs.reload();
            String provider = prefs.getString("translation_provider", "google");
            String targetLang = prefs.getString("target_translation_language", "");
            if (TextUtils.isEmpty(targetLang)) {
                targetLang = Locale.getDefault().getLanguage();
            }

            final EditText finalEntry = entry;
            Utils.showToast("Translating...", Toast.LENGTH_SHORT);

            translateStatic(textToTranslate, provider, targetLang)
                .thenAccept(translated -> {
                    if (!TextUtils.isEmpty(translated)) {
                        finalEntry.post(() -> {
                            finalEntry.setText(translated);
                            finalEntry.setSelection(translated.length());
                        });
                    }
                })
                .exceptionally(e -> {
                    finalEntry.post(() ->
                        Utils.showToast("Translation failed: " + e.getMessage(), Toast.LENGTH_SHORT)
                    );
                    return null;
                });
        } catch (Throwable e) {
            XposedBridge.log("GoogleTranslate: Button error: " + e.getMessage());
        }
    }

    // ==================== TEXT TOOLS POPUP MENU ====================

    // Menu item IDs
    private static final int MENU_TRANSLATE = 1;
    private static final int MENU_BOLD = 10;
    private static final int MENU_ITALIC = 11;
    private static final int MENU_STRIKETHROUGH = 12;
    private static final int MENU_MONOSPACE = 13;
    private static final int MENU_BULLETS = 14;
    private static final int MENU_NUMBERING = 15;
    private static final int MENU_REVERSE = 20;
    private static final int MENU_UPPERCASE = 21;
    private static final int MENU_LOWERCASE = 22;
    private static final int MENU_AI_FORMAL = 30;
    private static final int MENU_AI_CASUAL = 31;
    private static final int MENU_AI_SHORTER = 32;
    private static final int MENU_GRAMMAR_FIX = 33;
    private static final int MENU_AI_FORMAT = 34;
    private static final int MENU_ZALGO = 40;
    private static final int MENU_FANCY_SCRIPT = 41;
    private static final int MENU_FANCY_DOUBLE = 42;
    private static final int MENU_CANNED_RESPONSES = 50;
    private static final int MENU_INSERT_DATETIME = 51;
    private static final int MENU_HIDDEN_MSG = 100;

    // Category IDs for the main menu
    private static final int CAT_FORMATTING = 100;
    private static final int CAT_TRANSFORM = 101;
    private static final int CAT_AI_REWRITE = 102;
    private static final int CAT_FANCY_TEXT = 103;
    private static final int CAT_UTILITY = 104;

    /**
     * Shows a PopupMenu anchored to the translate button with text tool categories.
     * Tapping a category opens a second PopupMenu with that category's items.
     */
    private void showTextToolsMenu(View anchor, android.app.Activity activity) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        android.view.Menu menu = popup.getMenu();

        menu.add(0, MENU_TRANSLATE, 0, "\uD83C\uDF10 Translate");
        menu.add(0, CAT_FORMATTING, 1, "\u270F Formatting");
        menu.add(0, CAT_TRANSFORM, 2, "\u21C4 Transform");
        menu.add(0, CAT_AI_REWRITE, 3, "\uD83E\uDD16 AI Rewrite");
        menu.add(0, CAT_FANCY_TEXT, 4, "\u2728 Fancy Text");
        menu.add(0, CAT_UTILITY, 5, "\uD83D\uDCCB Utility");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_TRANSLATE) {
                onTranslateButtonClicked(activity);
                return true;
            }
            // Open category submenu
            showCategoryMenu(anchor, activity, id);
            return true;
        });

        applyRoundedCorners(popup);
        popup.show();
    }

    /**
     * Shows a second-level PopupMenu for a specific category, with a Back option.
     */
    private void showCategoryMenu(View anchor, android.app.Activity activity, int categoryId) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        android.view.Menu menu = popup.getMenu();

        switch (categoryId) {
            case CAT_FORMATTING:
                menu.add(0, MENU_BOLD, 0, "\uD835\uDDD5 Bold  *text*");
                menu.add(0, MENU_ITALIC, 1, "\uD835\uDC3C Italic  _text_");
                menu.add(0, MENU_STRIKETHROUGH, 2, "S\u0336 Strikethrough  ~text~");
                menu.add(0, MENU_MONOSPACE, 3, "{ } Monospace");
                menu.add(0, MENU_BULLETS, 4, "- Add Bullets");
                menu.add(0, MENU_NUMBERING, 5, "1. Add Numbers");
                break;
            case CAT_TRANSFORM:
                menu.add(0, MENU_REVERSE, 0, "\u21C4 Reverse Text");
                menu.add(0, MENU_UPPERCASE, 1, "ABC Uppercase");
                menu.add(0, MENU_LOWERCASE, 2, "abc Lowercase");
                break;
            case CAT_AI_REWRITE:
                menu.add(0, MENU_AI_FORMAL, 0, "\uD83C\uDF93 Formal");
                menu.add(0, MENU_AI_CASUAL, 1, "\uD83D\uDE0E Casual");
                menu.add(0, MENU_AI_SHORTER, 2, "\u2702 Shorter");
                menu.add(0, MENU_GRAMMAR_FIX, 3, "\u2714 Grammar Fix");
                menu.add(0, MENU_AI_FORMAT, 4, "\uD83D\uDCDD Smart Format");
                break;
            case CAT_FANCY_TEXT:
                menu.add(0, MENU_ZALGO, 0, "Z\u0335a\u0335l\u0335g\u0335o\u0335 Zalgo");
                menu.add(0, MENU_FANCY_SCRIPT, 1, "\uD835\uDCE2 Script");
                menu.add(0, MENU_FANCY_DOUBLE, 2, "\uD835\uDD4A Double-Struck");
                break;
            case CAT_UTILITY:
                menu.add(0, MENU_CANNED_RESPONSES, 0, "\uD83D\uDCDD Canned Responses");
                menu.add(0, MENU_INSERT_DATETIME, 1, "\uD83D\uDD52 Insert Date/Time");
                menu.add(0, MENU_HIDDEN_MSG, 2, "\uD83D\uDD34 Hidden Message");
                break;
        }

        // Back option at the bottom
        menu.add(0, -1, 99, "\u2190 Back");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == -1) {
                // Go back to main menu
                showTextToolsMenu(anchor, activity);
                return true;
            }
            handleTextToolSelected(id, activity);
            return true;
        });

        applyRoundedCorners(popup);
        popup.show();
    }

    /**
     * Applies rounded corners to a PopupMenu via reflection on its internal popup window.
     */
    private void applyRoundedCorners(PopupMenu popup) {
        try {
            java.lang.reflect.Field mPopupField = PopupMenu.class.getDeclaredField("mPopup");
            mPopupField.setAccessible(true);
            Object menuPopupHelper = mPopupField.get(popup);
            if (menuPopupHelper != null) {
                // Force showing icons (bonus) and get the popup window
                try {
                    Method setForceShowIcon = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon", boolean.class);
                    setForceShowIcon.setAccessible(true);
                    setForceShowIcon.invoke(menuPopupHelper, true);
                } catch (Exception ignored) {}

                // Try to access the popup's ListView after show() via a post callback
                // For pre-show, set the background on the popup window
                try {
                    Method getPopup = menuPopupHelper.getClass().getMethod("getPopup");
                    getPopup.setAccessible(true);
                    Object listPopupWindow = getPopup.invoke(menuPopupHelper);
                    if (listPopupWindow != null) {
                        Method getListView = listPopupWindow.getClass().getMethod("getListView");
                        View listView = (View) getListView.invoke(listPopupWindow);
                        if (listView != null) {
                            int dp12 = Utils.dipToPixels(12f);
                            GradientDrawable bg = new GradientDrawable();
                            bg.setCornerRadius(dp12);
                            boolean isDark = DesignUtils.isNightMode();
                            bg.setColor(isDark ? 0xFF1F1F1F : 0xFFFFFFFF);
                            listView.setBackground(bg);
                            listView.setClipToOutline(true);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Dispatches the selected text tool action.
     */
    private void handleTextToolSelected(int itemId, android.app.Activity activity) {
        switch (itemId) {
            case MENU_TRANSLATE:
                onTranslateButtonClicked(activity);
                break;
            case MENU_BOLD:
                wrapSelectedOrAll(activity, "*", "*");
                break;
            case MENU_ITALIC:
                wrapSelectedOrAll(activity, "_", "_");
                break;
            case MENU_STRIKETHROUGH:
                wrapSelectedOrAll(activity, "~", "~");
                break;
            case MENU_MONOSPACE:
                wrapSelectedOrAll(activity, "```", "```");
                break;
            case MENU_BULLETS:
                addBullets(activity);
                break;
            case MENU_NUMBERING:
                addNumbering(activity);
                break;
            case MENU_REVERSE:
                transformText(activity, text -> new StringBuilder(text).reverse().toString());
                break;
            case MENU_UPPERCASE:
                transformText(activity, text -> text.toUpperCase(Locale.getDefault()));
                break;
            case MENU_LOWERCASE:
                transformText(activity, text -> text.toLowerCase(Locale.getDefault()));
                break;
            case MENU_AI_FORMAL:
                aiRewrite(activity, "Rewrite the following text in a formal, professional tone. Provide ONLY the rewritten text without any explanations.");
                break;
            case MENU_AI_CASUAL:
                aiRewrite(activity, "Rewrite the following text in a casual, friendly tone. Provide ONLY the rewritten text without any explanations.");
                break;
            case MENU_AI_SHORTER:
                aiRewrite(activity, "Rewrite the following text to be shorter and more concise while keeping the same meaning. Provide ONLY the rewritten text without any explanations.");
                break;
            case MENU_GRAMMAR_FIX:
                aiRewrite(activity, "Fix the grammar and spelling of the following text. Keep the same language and meaning. Provide ONLY the corrected text without any explanations.");
                break;
            case MENU_AI_FORMAT:
                aiRewrite(activity, "Format the following text nicely using WhatsApp-style formatting: use *bold* for emphasis, - for bullet points, and 1. for numbered lists. Organize the content into clear sections if appropriate. Provide ONLY the formatted text without any explanations.");
                break;
            case MENU_ZALGO:
                transformText(activity, GoogleTranslate::toZalgo);
                break;
            case MENU_FANCY_SCRIPT:
                transformText(activity, GoogleTranslate::toFancyScript);
                break;
            case MENU_FANCY_DOUBLE:
                transformText(activity, GoogleTranslate::toFancyDoubleStruck);
                break;
            case MENU_CANNED_RESPONSES:
                showCannedResponses(activity);
                break;
            case MENU_INSERT_DATETIME:
                insertDateTime(activity);
                break;
            case MENU_HIDDEN_MSG:
                transformText(activity, GoogleTranslate::toHiddenMessage);
                break;
        }
    }

    // ==================== HELPER: Get Entry EditText ====================

    private EditText getEntryEditText(android.app.Activity activity) {
        int entryId = Utils.getID("entry", "id");
        if (entryId != -1) {
            View v = activity.findViewById(entryId);
            if (v instanceof EditText) return (EditText) v;
        }
        // Fallback: search for visible EditTexts
        View rootView = activity.getWindow().getDecorView();
        java.util.ArrayList<EditText> edits = new java.util.ArrayList<>();
        findEditTexts(rootView, edits);
        for (EditText e : edits) {
            if (e.getVisibility() == View.VISIBLE && e.isEnabled()) {
                return e;
            }
        }
        return null;
    }

    // ==================== TEXT FORMATTING (Wrap) ====================

    /**
     * Wraps selected text (or all text if no selection) with prefix/suffix.
     * Supports WhatsApp formatting: *bold*, _italic_, ~strikethrough~, ```monospace```
     */
    private void wrapSelectedOrAll(android.app.Activity activity, String prefix, String suffix) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }
        String fullText = entry.getText().toString();
        if (fullText.isEmpty()) {
            Utils.showToast("Type a message first", Toast.LENGTH_SHORT);
            return;
        }

        int selStart = entry.getSelectionStart();
        int selEnd = entry.getSelectionEnd();

        String result;
        int newCursorPos;
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
            // Wrap only selected text
            String before = fullText.substring(0, selStart);
            String selected = fullText.substring(selStart, selEnd);
            String after = fullText.substring(selEnd);
            result = before + prefix + selected + suffix + after;
            newCursorPos = selStart + prefix.length() + selected.length() + suffix.length();
        } else {
            // Wrap all text
            result = prefix + fullText + suffix;
            newCursorPos = result.length();
        }

        entry.setText(result);
        entry.setSelection(Math.min(newCursorPos, result.length()));
    }

    /**
     * Adds bullet points (•) to each line of the selected text or all text.
     */
    private void addBullets(android.app.Activity activity) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }

        int selStart = entry.getSelectionStart();
        int selEnd = entry.getSelectionEnd();
        String fullText = entry.getText().toString();

        if (fullText.isEmpty()) {
            Utils.showToast("Type a message first", Toast.LENGTH_SHORT);
            return;
        }

        String result;
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
            // Add bullets only to selected lines
            String before = fullText.substring(0, selStart);
            String selected = fullText.substring(selStart, selEnd);
            String after = fullText.substring(selEnd);
            String[] lines = selected.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    sb.append("- ").append(line).append("\n");
                } else {
                    sb.append(line).append("\n");
                }
            }
            result = before + sb.toString().trim() + after;
        } else {
            // Add bullets to all non-empty lines
            String[] lines = fullText.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    sb.append("- ").append(line);
                } else {
                    sb.append(line);
                }
                sb.append("\n");
            }
            result = sb.toString().trim();
        }

        entry.setText(result);
    }

    /**
     * Adds numbered list (1., 2., 3., etc.) to each line of the selected text or all text.
     */
    private void addNumbering(android.app.Activity activity) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }

        int selStart = entry.getSelectionStart();
        int selEnd = entry.getSelectionEnd();
        String fullText = entry.getText().toString();

        if (fullText.isEmpty()) {
            Utils.showToast("Type a message first", Toast.LENGTH_SHORT);
            return;
        }

        String result;
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
            // Add numbering only to selected lines
            String before = fullText.substring(0, selStart);
            String selected = fullText.substring(selStart, selEnd);
            String after = fullText.substring(selEnd);
            String[] lines = selected.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            int num = 1;
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    sb.append(num++).append(". ").append(line).append("\n");
                } else {
                    sb.append(line).append("\n");
                }
            }
            result = before + sb.toString().trim() + after;
        } else {
            // Add numbering to all non-empty lines
            String[] lines = fullText.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            int num = 1;
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    sb.append(num++).append(". ").append(line);
                } else {
                    sb.append(line);
                }
                sb.append("\n");
            }
            result = sb.toString().trim();
        }

        entry.setText(result);
    }

    // ==================== TEXT TRANSFORMATION ====================

    @FunctionalInterface
    private interface TextTransformer {
        String transform(String text);
    }

    /**
     * Transforms selected text (or all text if no selection) using the provided function.
     */
    private void transformText(android.app.Activity activity, TextTransformer transformer) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }
        String fullText = entry.getText().toString();
        if (fullText.isEmpty()) {
            Utils.showToast("Type a message first", Toast.LENGTH_SHORT);
            return;
        }

        int selStart = entry.getSelectionStart();
        int selEnd = entry.getSelectionEnd();

        String result;
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
            String before = fullText.substring(0, selStart);
            String selected = fullText.substring(selStart, selEnd);
            String after = fullText.substring(selEnd);
            result = before + transformer.transform(selected) + after;
        } else {
            result = transformer.transform(fullText);
        }

        entry.setText(result);
        entry.setSelection(result.length());
    }

    // ==================== AI REWRITE / GRAMMAR FIX ====================

    /**
     * Sends the text to the configured LLM provider with a custom system prompt.
     * Reuses the existing Groq/Gemini infrastructure.
     */
    private void aiRewrite(android.app.Activity activity, String systemPrompt) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }
        String text = entry.getText().toString().trim();
        if (text.isEmpty()) {
            Utils.showToast("Type a message first", Toast.LENGTH_SHORT);
            return;
        }

        prefs.reload();
        String provider = prefs.getString("translation_provider", "google");

        if ("google".equals(provider)) {
            Utils.showToast("AI features require Groq or Gemini provider.\nSet it in WaEnhancer settings.", Toast.LENGTH_LONG);
            return;
        }

        Utils.showToast("Processing...", Toast.LENGTH_SHORT);

        CompletableFuture<String> future;
        if ("groq".equals(provider)) {
            future = llmRequestGroq(text, systemPrompt, WppXposed.getPref());
        } else {
            future = llmRequestGemini(text, systemPrompt, WppXposed.getPref());
        }

        future.thenAccept(result -> {
            if (!TextUtils.isEmpty(result)) {
                entry.post(() -> {
                    entry.setText(result);
                    entry.setSelection(result.length());
                });
            }
        }).exceptionally(e -> {
            entry.post(() ->
                Utils.showToast("AI processing failed: " + e.getMessage(), Toast.LENGTH_SHORT)
            );
            return null;
        });
    }

    /**
     * Generic Groq LLM request with a custom system prompt.
     */
    private static CompletableFuture<String> llmRequestGroq(String text, String systemPrompt, XSharedPreferences prefs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        prefs.reload();
        String apiKey = prefs.getString("groq_api_key", "").trim();
        if (TextUtils.isEmpty(apiKey)) {
            future.complete(text + " [Groq API key not provided]");
            return future;
        }

        try {
            JSONObject root = new JSONObject();
            root.put("model", "llama-3.1-8b-instant");

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", text);
            messages.put(userMsg);

            root.put("messages", messages);
            root.put("temperature", 0.3);

            Request request = new Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(root.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        if (!response.isSuccessful()) {
                            future.complete(text + " [Groq Error: " + response.code() + "]");
                            return;
                        }
                        JSONObject result = new JSONObject(body);
                        future.complete(result.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content").trim());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Generic Gemini LLM request with a custom system prompt.
     */
    private static CompletableFuture<String> llmRequestGemini(String text, String systemPrompt, XSharedPreferences prefs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        prefs.reload();
        String apiKey = prefs.getString("gemini_api_key", "").trim();
        String modelName = prefs.getString("gemini_model", "gemini-1.5-flash").trim();

        if (TextUtils.isEmpty(apiKey)) {
            future.complete(text + " [Gemini API key not provided]");
            return future;
        }

        try {
            JSONObject root = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray partsArray = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("text", systemPrompt + "\n\nText: " + text);
            partsArray.put(textPart);

            contentObj.put("parts", partsArray);
            contentsArray.put(contentObj);
            root.put("contents", contentsArray);

            Request request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/" + modelName
                            + ":generateContent?key=" + apiKey)
                    .post(RequestBody.create(root.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        if (!response.isSuccessful()) {
                            future.complete(text + " [Gemini Error: " + response.code() + "]");
                            return;
                        }
                        JSONObject jsonResult = new JSONObject(body);
                        future.complete(jsonResult.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text").trim());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    // ==================== ZALGO TEXT ====================

    private static final char[] ZALGO_UP = {
        '\u0300', '\u0301', '\u0302', '\u0303', '\u0304', '\u0305', '\u0306', '\u0307',
        '\u0308', '\u0309', '\u030A', '\u030B', '\u030C', '\u030D', '\u030E', '\u030F',
        '\u0310', '\u0311', '\u0312', '\u0313', '\u0314', '\u0315', '\u031A', '\u033D',
        '\u034A', '\u034B', '\u034C', '\u0350', '\u0351', '\u0352', '\u0357', '\u035B'
    };
    private static final char[] ZALGO_MID = {
        '\u0315', '\u031B', '\u0334', '\u0335', '\u0336', '\u0337', '\u0338', '\u0340',
        '\u0341', '\u0358', '\u0321', '\u0322', '\u0327', '\u0328', '\u0334', '\u0335'
    };
    private static final char[] ZALGO_DOWN = {
        '\u0316', '\u0317', '\u0318', '\u0319', '\u031C', '\u031D', '\u031E', '\u031F',
        '\u0320', '\u0323', '\u0324', '\u0325', '\u0326', '\u0329', '\u032A', '\u032B',
        '\u032C', '\u032D', '\u032E', '\u032F', '\u0330', '\u0331', '\u0332', '\u0333',
        '\u0339', '\u033A', '\u033B', '\u033C', '\u0345', '\u0347', '\u0348', '\u0349'
    };

    private static String toZalgo(String text) {
        Random rng = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == ' ' || c == '\n') continue;
            // Add 1-3 combining marks above, middle, and below
            int numUp = 1 + rng.nextInt(3);
            int numMid = rng.nextInt(2);
            int numDown = 1 + rng.nextInt(3);
            for (int j = 0; j < numUp; j++) sb.append(ZALGO_UP[rng.nextInt(ZALGO_UP.length)]);
            for (int j = 0; j < numMid; j++) sb.append(ZALGO_MID[rng.nextInt(ZALGO_MID.length)]);
            for (int j = 0; j < numDown; j++) sb.append(ZALGO_DOWN[rng.nextInt(ZALGO_DOWN.length)]);
        }
        return sb.toString();
    }

    // ==================== HIDDEN MESSAGE (Zero-Width Characters) ====================

    private static final char ZERO_WIDTH_SPACE = '\u200B';
    private static final char ZERO_WIDTH_NON_JOINER = '\u200C';
    private static final char ZERO_WIDTH_JOINER = '\u200D';
    private static final char LEFT_TO_RIGHT_MARK = '\u200E';
    private static final char RIGHT_TO_LEFT_MARK = '\u200F';

    private static String toHiddenMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        Random rng = new Random();
        for (int i = 0; i < 100 + rng.nextInt(100); i++) {
            int r = rng.nextInt(5);
            switch (r) {
                case 0: sb.append(ZERO_WIDTH_SPACE); break;
                case 1: sb.append(ZERO_WIDTH_NON_JOINER); break;
                case 2: sb.append(ZERO_WIDTH_JOINER); break;
                case 3: sb.append(LEFT_TO_RIGHT_MARK); break;
                case 4: sb.append(RIGHT_TO_LEFT_MARK); break;
            }
        }
        return sb.toString();
    }

    // ==================== FANCY TEXT (Unicode Math Styles) ====================

    /**
     * Converts ASCII letters to Mathematical Script Unicode characters.
     * a-z → U+1D4B6..U+1D4CF, A-Z → U+1D49C..U+1D4B5
     */
    private static String toFancyScript(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.appendCodePoint(0x1D49C + (c - 'A'));
            } else if (c >= 'a' && c <= 'z') {
                sb.appendCodePoint(0x1D4B6 + (c - 'a'));
            } else if (c >= '0' && c <= '9') {
                sb.append(c); // No script digits in Unicode
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts ASCII letters to Double-Struck (Blackboard Bold) Unicode characters.
     * A-Z → U+1D538..U+1D551, a-z → U+1D552..U+1D56B, 0-9 → U+1D7D8..U+1D7E1
     */
    private static String toFancyDoubleStruck(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.appendCodePoint(0x1D538 + (c - 'A'));
            } else if (c >= 'a' && c <= 'z') {
                sb.appendCodePoint(0x1D552 + (c - 'a'));
            } else if (c >= '0' && c <= '9') {
                sb.appendCodePoint(0x1D7D8 + (c - '0'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== CANNED RESPONSES ====================

    private static final String[] DEFAULT_CANNED_RESPONSES = {
        "I'll get back to you shortly.",
        "Thanks for your message!",
        "I'm busy right now, will reply later.",
        "On my way!",
        "Sounds good!",
        "Let me check and get back to you.",
        "Good morning!",
        "Good night!",
        "Happy birthday! \uD83C\uDF82",
        "Congratulations! \uD83C\uDF89"
    };

    /**
     * Shows a dialog with canned responses to quickly insert into the text field.
     */
    private void showCannedResponses(android.app.Activity activity) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Quick Responses");
        builder.setItems(DEFAULT_CANNED_RESPONSES, (dialog, which) -> {
            String response = DEFAULT_CANNED_RESPONSES[which];
            String current = entry.getText().toString();
            if (current.isEmpty()) {
                entry.setText(response);
            } else {
                // Append to existing text
                String newText = current + " " + response;
                entry.setText(newText);
            }
            entry.setSelection(entry.getText().length());
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ==================== INSERT DATE/TIME ====================

    /**
     * Inserts the current date and time at the cursor position or appends to the text.
     */
    private void insertDateTime(android.app.Activity activity) {
        EditText entry = getEntryEditText(activity);
        if (entry == null) {
            Utils.showToast("Could not find message input", Toast.LENGTH_SHORT);
            return;
        }

        String[] formats = {
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "MMM dd, yyyy h:mm a",
            "EEEE, MMMM dd, yyyy",
            "HH:mm",
            "h:mm a"
        };

        String[] labels = new String[formats.length];
        Date now = new Date();
        for (int i = 0; i < formats.length; i++) {
            labels[i] = new SimpleDateFormat(formats[i], Locale.getDefault()).format(now);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Insert Date/Time");
        builder.setItems(labels, (dialog, which) -> {
            String dateStr = labels[which];
            int cursor = entry.getSelectionStart();
            String current = entry.getText().toString();

            String result;
            if (cursor >= 0 && cursor <= current.length()) {
                result = current.substring(0, cursor) + dateStr + current.substring(cursor);
            } else {
                result = current + dateStr;
            }
            entry.setText(result);
            entry.setSelection(Math.min(cursor + dateStr.length(), result.length()));
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ==================== UTILITY ====================

    private static void findEditTexts(View view, java.util.ArrayList<EditText> list) {
        if (view instanceof EditText) {
            list.add((EditText) view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findEditTexts(group.getChildAt(i), list);
            }
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Message Translation";
    }
}
