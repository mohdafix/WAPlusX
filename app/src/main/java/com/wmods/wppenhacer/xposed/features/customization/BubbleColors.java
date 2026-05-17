package com.wmods.wppenhacer.xposed.features.customization;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.MonetColorEngine;
import com.wmods.wppenhacer.xposed.utils.Utils;
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BubbleColors extends Feature {
    private static final String TAG = "WAE_BubbleColors";
    private static final ThreadLocal<Boolean> mIsInternalCall = new ThreadLocal<>();

    public BubbleColors(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    private int getBubbleColor(boolean isRight) {
        if (prefs.getBoolean("bubble_color", false)) {
            int manual = isRight ? prefs.getInt("bubble_right", 0) : prefs.getInt("bubble_left", 0);
            if (manual != 0)
                return manual;
        }

        if (prefs.getBoolean("monet_theme", false)) {
            if (!isRight) {
                // Return a theme-aware neutral color instead of hardcoded BLACK
                return DesignUtils.isNightMode() ? 0xFF202C33 : 0xFFFFFFFF;
            }
            try {
                int monet = MonetColorEngine.getBubbleOutgoingColor(Utils.getApplication());
                if (monet != -1)
                    return monet;
            } catch (Exception ignored) {
            }
        }

        boolean isDark = DesignUtils.isNightMode();
        if (isRight)
            return isDark ? 0xFF005C4B : 0xFFD9FDD3;
        return isDark ? 0xFF202C33 : 0xFFFFFFFF;
    }

    @Override
    public void doHook() throws Exception {

        // 1. TAGGING - Tag the drawables with their color
        XC_MethodHook bubbleTagHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var drawable = (Drawable) param.getResult();
                if (drawable == null)
                    return;

                int position = -1;
                if (param.args.length > 0 && param.args[0] instanceof Integer) {
                    position = (int) param.args[0];
                } else if (param.args.length > 1 && param.args[1] instanceof Integer) {
                    position = (int) param.args[1];
                }

                if (position == -1)
                    return;
                boolean isRight = (position == 3);

                if (!BubbleThemes.isBubbleThemeActive) {
                    int color = getBubbleColor(isRight);
                    if (prefs.getBoolean("bubble_color", false)) {
                        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                    }
                    Utils.tagDrawable(drawable, (isRight ? "bubble_right:" : "bubble_left:") + color);
                } else {
                    String current = Utils.getTickerName(drawable);
                    if (current == null) {
                        Utils.tagDrawable(drawable, isRight ? "bubble_right" : "bubble_left");
                    }
                }
            }
        };

        XposedBridge.hookMethod(Unobfuscator.loadBallonDateDrawable(classLoader), bubbleTagHook);
        XposedBridge.hookMethod(Unobfuscator.loadBallonBorderDrawable(classLoader), bubbleTagHook);
        XposedBridge.hookMethod(Unobfuscator.loadBubbleDrawableMethod(classLoader), bubbleTagHook);

        // 2. BINDING HOOK - Apply color when message is bound
        // This is the ONLY hook we need - it properly targets only conversation items
        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup, int position, View convertView) {
                applyColorToTree(viewGroup, fMessage.getKey().isFromMe);
            }
        });
    }

    private void applyColorToTree(View view, boolean isRight) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (isMessageText(tv)) {
                applyTargetColor(tv, isRight);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyColorToTree(vg.getChildAt(i), isRight);
            }
        }
    }

    private boolean isMessageText(TextView tv) {
        int id = tv.getId();
        if (id == View.NO_ID)
            return false;
        try {
            String name = tv.getResources().getResourceEntryName(id);
            // Only color the main message text and media captions
            // Skip: participant names, timestamps, dates, forwarded labels, etc.
            return "message_text".equals(name) ||
                    "caption".equals(name);
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isLikelyRightBubble(View rowView) {
        // Use correctly tagged fMessage from ConversationItemListener
        Object fmessage = XposedHelpers.getAdditionalInstanceField(rowView, "fMessage");
        if (fmessage instanceof FMessageWpp) {
            return ((FMessageWpp) fmessage).getKey().isFromMe;
        }

        // Fallback to background tag
        Drawable bg = rowView.getBackground();
        String tag = Utils.getTickerName(bg);
        if (tag != null && tag.startsWith("bubble_")) {
            return tag.contains("right");
        }

        // Fallback to children search
        if (rowView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) rowView;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Drawable cbg = vg.getChildAt(i).getBackground();
                String ctag = Utils.getTickerName(cbg);
                if (ctag != null && ctag.startsWith("bubble_")) {
                    return ctag.contains("right");
                }
            }
        }

        return false;
    }

    private void applyTargetColor(TextView textView, boolean isRight) {
        prefs.reload();
        int manualText = isRight ? prefs.getInt("bubble_text_right", 0) : prefs.getInt("bubble_text_left", 0);
        int targetColor = 0;

        if (manualText != 0) {
            targetColor = manualText;
        } else {
            int bColor = getBubbleColor(isRight);
            targetColor = DesignUtils.getContrastColor(bColor);
        }

        if (targetColor != 0) {
            mIsInternalCall.set(true);
            try {
                if (textView.getCurrentTextColor() != targetColor) {
                    textView.setTextColor(targetColor);
                }
                if (textView.getCurrentHintTextColor() != targetColor) {
                    textView.setHintTextColor(targetColor);
                }
            } finally {
                mIsInternalCall.set(false);
            }
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Bubble Colors";
    }
}
