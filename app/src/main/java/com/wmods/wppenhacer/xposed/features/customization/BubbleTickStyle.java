package com.wmods.wppenhacer.xposed.features.customization;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewStub;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * BubbleTickStyle - Ported from decompiled ng.java
 *
 * Replaces custom tick drawables via targeted hooks:
 * 1. XResForwarder resource replacement (done in WppXposed)
 * 2. BubbleRelativeLayout constructor - resize status ImageView
 * 3. ViewHolder constructor - resize status_indicator ImageView
 * 4. ViewStub.inflate - resize status_indicator after inflate
 * 5. ImageView.setImageTintList - block WhatsApp tint on own-message bubble ticks
 * 6. Contact drawable status method - return custom drawables for chat list
 */
public class BubbleTickStyle extends Feature {

    private static final String MAIN_LAYOUT_TAG = "wae_main_layout";
    private static final Object NON_TICK_SENTINEL = Boolean.FALSE;
    private static final ThreadLocal<Boolean> mIsInternalCall = new ThreadLocal<>();
    private static boolean conversationListenerRegistered;
    
    private static java.lang.reflect.Method mGetFMessageMethod;
    private static int mTickSizePx = -1;

    public BubbleTickStyle(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    /**
     * Resolve a module drawable resource ID for the given tick style and status suffix.
     * Tries "{style}_message_{suffix}" first, falls back to "ab_message_{suffix}".
     */
     private static int resolveTickResId(String style, String suffix) {
        int resId = 0;
        try {
            resId = R.drawable.class.getField(style + "_message_" + suffix).getInt(null);
        } catch (Throwable ignored) {
        }
        if (resId != 0) return resId;

        // Fallback to _onmedia if standard is missing for this style
        if (!suffix.endsWith("_onmedia")) {
            try {
                resId = R.drawable.class.getField(style + "_message_" + suffix + "_onmedia").getInt(null);
            } catch (Throwable ignored) {}
        }
        if (resId != 0) return resId;

        try {
            return R.drawable.class.getField("ab_message_" + suffix).getInt(null);
        } catch (Throwable ignored) {
            // Last resort: try ab_message_..._onmedia
            if (!suffix.endsWith("_onmedia")) {
                try {
                    return R.drawable.class.getField("ab_message_" + suffix + "_onmedia").getInt(null);
                } catch (Throwable ignored2) {}
            }
            return 0;
        }
    }

    private static int resolveTickResIdForStatus(int status, int sentResId, int deliveredResId,
            int readResId, int unsentResId) {
        if (status == 1 || status == 4) {
            return sentResId;
        }
        if (status == 5) {
            return deliveredResId;
        }
        if (status == 8 || status == 13 || status == 16) {
            return readResId;
        }
        if (status == 0 || status == 20) {
            return unsentResId;
        }
        return 0;
    }

    private void applyBubbleTick(ViewGroup viewGroup, FMessageWpp fMessage, int sentResId,
            int deliveredResId, int readResId, int unsentResId) {
        FMessageWpp.Key key = fMessage.getKey();
        if (key == null || !key.isFromMe) return;

        String[] ids = {"status", "status_indicator", "connection_status_indicator", "connection_media_status_indicator"};
        for (String idName : ids) {
            int id = Utils.getID(idName, "id");
            if (id <= 0) continue;

            ImageView statusView = viewGroup.findViewById(id);
            if (statusView == null) continue;

            int resId = resolveTickResIdForStatus(fMessage.getStatus(), sentResId, deliveredResId,
                    readResId, unsentResId);
            if (resId == 0) continue;

            XposedHelpers.setAdditionalInstanceField(statusView, MAIN_LAYOUT_TAG, viewGroup);
            statusView.setImageResource(resId);
            // Clear any existing tint/filter to prevent blue flash
            statusView.setImageTintList(null);
            statusView.setColorFilter(null);
        }
    }

    @Override
    public void doHook() throws Throwable {
        String style = prefs.getString("bubble_tick_style", "default");
        if (style == null || style.trim().isEmpty()) {
            style = "default";
        } else {
            style = style.trim();
        }
        if ("default".equals(style)) return;

        if (mTickSizePx == -1) {
            mTickSizePx = Utils.dipToPixels(20);
        }

        // Resolve all custom drawable resource IDs
        int resSent = resolveTickResId(style, "got_receipt_from_server");
        int resDelivered = resolveTickResId(style, "got_receipt_from_target");
        int resRead = resolveTickResId(style, "got_read_receipt_from_target");
        int resUnsent = resolveTickResId(style, "unsent");

        int resSentOnMedia = resolveTickResId(style, "got_receipt_from_server_onmedia");
        int resDeliveredOnMedia = resolveTickResId(style, "got_receipt_from_target_onmedia");
        int resReadOnMedia = resolveTickResId(style, "got_read_receipt_from_target_onmedia");
        int resUnsentOnMedia = resolveTickResId(style, "unsent_onmedia");

        // Fallback for missing read recipes: use onmedia version if available, otherwise delivered
        final boolean readFallback = (resRead == 0);
        if (readFallback) {
            resRead = (resReadOnMedia != 0) ? resReadOnMedia : resDelivered;
        }

        final boolean readOnMediaFallback = (resReadOnMedia == 0);
        if (readOnMediaFallback) {
            resReadOnMedia = (resRead != 0 && !readFallback) ? resRead : resDeliveredOnMedia;
        }

        if (!conversationListenerRegistered) {
            final int fSent = resSent;
            final int fDelivered = resDelivered;
            final int fRead = resRead;
            final int fUnsent = resUnsent;
            ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
                @Override
                public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup, int position, View convertView) {
                    applyBubbleTick(viewGroup, fMessage, fSent, fDelivered, fRead, fUnsent);
                }
            });
            conversationListenerRegistered = true;
        }

        // --- Hook 1: status resource methods ---
        try {
            java.lang.reflect.Method tickResMethod = Unobfuscator.loadGetTickResourceIdMethod(classLoader);
            final int fSent = resSent;
            final int fDelivered = resDelivered;
            final int fRead = resRead;
            final int fUnsent = resUnsent;
            XposedBridge.hookMethod(tickResMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int status = (int) param.args[0];
                    int customResId = resolveTickResIdForStatus(status, fSent, fDelivered, fRead, fUnsent);
                    if (customResId != 0) {
                        param.setResult(customResId);
                    }
                }
            });

            java.lang.reflect.Method tickResOnMediaMethod = Unobfuscator.loadGetTickResourceIdOnMediaMethod(classLoader);
            final int fSentM = resSentOnMedia;
            final int fDeliveredM = resDeliveredOnMedia;
            final int fReadM = resReadOnMedia;
            final int fUnsentM = resUnsentOnMedia;
            XposedBridge.hookMethod(tickResOnMediaMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int status = (int) param.args[0];
                    int customResId = resolveTickResIdForStatus(status, fSentM, fDeliveredM, fReadM, fUnsentM);
                    if (customResId != 0) {
                        param.setResult(customResId);
                    }
                }
            });
        } catch (Throwable e) {
            log("Failed to hook tick resource methods: " + e.getMessage());
        }

        // --- Hook 2: BubbleRelativeLayout/ConversationRow constructors ---
        try {
            Class<?> bubbleClass = Unobfuscator.loadBubbleRelativeLayoutClass(classLoader);
            Class<?> convRowClass = Unobfuscator.loadConversationRowClass(classLoader);
            
            XC_MethodHook constructorHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        View thisView = (View) param.thisObject;
                        String[] ids = {"status", "status_indicator", "connection_status_indicator", "connection_media_status_indicator"};
                        for (String idName : ids) {
                            int id = Utils.getID(idName, "id");
                            if (id <= 0) continue;
                            ImageView statusView = thisView.findViewById(id);
                            if (statusView == null) continue;

                            int size = mTickSizePx;
                            ViewGroup.LayoutParams lp = statusView.getLayoutParams();
                            if (lp != null) {
                                lp.height = size;
                                lp.width = size;
                            }
                            statusView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            XposedHelpers.setAdditionalInstanceField(statusView, MAIN_LAYOUT_TAG, param.thisObject);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            
            XposedBridge.hookAllConstructors(bubbleClass, constructorHook);
            XposedBridge.hookAllConstructors(convRowClass, constructorHook);
        } catch (Throwable e) {
            log("Failed to hook bubble/row constructors: " + e.getMessage());
        }

        // --- Hook 3: ViewHolder constructors (Chat list indicators) ---
        try {
            Class<?> viewHolderClass = Unobfuscator.loadViewHolder(classLoader);
            XposedBridge.hookAllConstructors(viewHolderClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args.length < 2) return;
                        Object arg1 = param.args[1];
                        if (!(arg1 instanceof View itemView)) return;

                        int indicatorId = Utils.getID("status_indicator", "id");
                        if (indicatorId <= 0) return;
                        ImageView indicator = itemView.findViewById(indicatorId);
                        if (indicator == null) return;

                        int size = mTickSizePx;
                        ViewGroup.LayoutParams lp = indicator.getLayoutParams();
                        if (lp != null) {
                            lp.height = size;
                            lp.width = size;
                        }
                        indicator.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable e) {
            log(e);
        }

        // --- Hook 4: ViewStub.inflate ---
        XposedHelpers.findAndHookMethod(ViewStub.class, "inflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    ViewStub stub = (ViewStub) param.thisObject;
                    int stubId = Utils.getID("status_indicator_stub", "id");
                    if (stubId <= 0 || stub.getId() != stubId) return;

                    View inflated = (View) param.getResult();
                    if (inflated == null) return;

                    int indicatorId = Utils.getID("status_indicator", "id");
                    if (indicatorId <= 0) return;
                    ImageView indicator = inflated.findViewById(indicatorId);
                    if (indicator == null) return;

                    int size = mTickSizePx;
                    ViewGroup.LayoutParams lp = indicator.getLayoutParams();
                    if (lp != null) {
                        lp.height = size;
                        lp.width = size;
                    }
                    indicator.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    // Tag it with its parent inflated view so interceptor knows it's a tick
                    XposedHelpers.setAdditionalInstanceField(indicator, MAIN_LAYOUT_TAG, inflated);
                } catch (Throwable ignored) {
                }
            }
        });

        // --- Hook 5: ImageView Ticker Interceptor ---
        final int fSentB = resSent;
        final int fDeliveredB = resDelivered;
        final int fReadB = resRead;
        final int fUnsentB = resUnsent;

        XC_MethodHook tickerInterceptor = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Boolean.TRUE.equals(mIsInternalCall.get())) return;
                try {
                    ImageView imageView = (ImageView) param.thisObject;
                    
                    // Memoization: check if we already identified this view
                    Object mainLayout = XposedHelpers.getAdditionalInstanceField(imageView, MAIN_LAYOUT_TAG);
                    if (mainLayout == NON_TICK_SENTINEL) return;
                    
                    if (mainLayout == null) {
                        // Not tagged yet. Perform one-time discovery traversal.
                        ViewParent p = imageView.getParent();
                        int depth = 0;
                        while (p instanceof View && depth < 5) {
                            mainLayout = XposedHelpers.getAdditionalInstanceField(p, MAIN_LAYOUT_TAG);
                            if (mainLayout != null && mainLayout != NON_TICK_SENTINEL) {
                                // Found it in parent. Cache it on the ImageView itself.
                                XposedHelpers.setAdditionalInstanceField(imageView, MAIN_LAYOUT_TAG, mainLayout);
                                break;
                            }
                            p = p.getParent();
                            depth++;
                        }
                        
                        if (mainLayout == null) {
                            // Still not found after traversal. Mark as non-tick to skip next time.
                            XposedHelpers.setAdditionalInstanceField(imageView, MAIN_LAYOUT_TAG, NON_TICK_SENTINEL);
                            return;
                        }
                    }

                    // Efficiently get FMessage from layout
                    if (mGetFMessageMethod == null) {
                        mGetFMessageMethod = com.wmods.wppenhacer.xposed.utils.ReflectionUtils.findMethodUsingFilter(
                            mainLayout.getClass(), m -> m.getName().equals("getFMessage") && m.getParameterCount() == 0
                        );
                    }
                    
                    Object rawFMessage = mGetFMessageMethod != null ? mGetFMessageMethod.invoke(mainLayout) : null;
                    if (rawFMessage == null) return;

                    FMessageWpp fMessage = new FMessageWpp(rawFMessage);
                    FMessageWpp.Key key = fMessage.getKey();
                    if (key == null || !key.isFromMe) return;

                    int status = fMessage.getStatus();
                    int resId = resolveTickResIdForStatus(status, fSentB, fDeliveredB, fReadB, fUnsentB);
                    if (resId != 0) {
                        // Check if it's already set to prevent loop
                        if (param.method.getName().equals("setImageResource")) {
                            if ((int) param.args[0] == resId) return;
                        }

                        mIsInternalCall.set(true);
                        try {
                            imageView.setImageResource(resId);
                            // Clear tints and filters for custom icons
                            imageView.setImageTintList(null);
                            imageView.setColorFilter(null);
                        } finally {
                            mIsInternalCall.set(false);
                        }
                        
                        // Block native call if it would apply a tint/filter or set a native res
                        boolean isReadStatus = (status == 8 || status == 13 || status == 16);
                        if (isReadStatus && readFallback && resId == fDeliveredB) {
                            return; // Allow native blue tint
                        }
                        if (isReadStatus && fMessage.isMediaFile() && readOnMediaFallback && resId == resDeliveredOnMedia) {
                            return; // Allow native blue tint
                        }
                        
                        param.setResult(null);
                    }
                } catch (Throwable ignored) {
                }
            }
        };

        XposedHelpers.findAndHookMethod(ImageView.class, "setImageTintList",
                android.content.res.ColorStateList.class, tickerInterceptor);
        XposedHelpers.findAndHookMethod(ImageView.class, "setColorFilter",
                android.graphics.ColorFilter.class, tickerInterceptor);
        XposedHelpers.findAndHookMethod(ImageView.class, "setColorFilter",
                int.class, android.graphics.PorterDuff.Mode.class, tickerInterceptor);
        XposedHelpers.findAndHookMethod(ImageView.class, "setImageResource",
                int.class, tickerInterceptor);
        XposedHelpers.findAndHookMethod(ImageView.class, "setImageDrawable",
                android.graphics.drawable.Drawable.class, tickerInterceptor);

        // --- Hook 6: Contact drawable status method (Chat list) ---
        final int resSentC = resSent;
        final int resDeliveredC = resDelivered;
        final int resReadC = resRead;
        final int resUnsentC = resUnsent;
        try {
            java.lang.reflect.Method contactDrawableMethod =
                    Unobfuscator.loadGetContactDrawableStatusMethod(classLoader);
            XposedBridge.hookMethod(contactDrawableMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object rawFMessage = findFMessageInArgs(param.args);
                        if (rawFMessage == null) return;

                        FMessageWpp fMessage = new FMessageWpp(rawFMessage);
                        FMessageWpp.Key key = fMessage.getKey();
                        if (key == null || !key.isFromMe) return;

                        int status = fMessage.getStatus();
                        int resId = resolveTickResIdForStatus(status, resSentC, resDeliveredC, resReadC, resUnsentC);
                        if (resId == 0) return;

                        Drawable drawable = Utils.getApplication().getDrawable(resId);
                        if (drawable != null) {
                            param.setResult(drawable);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable e) {
            log("Failed to hook contact drawable method: " + e.getMessage());
        }
    }

    /**
     * Search method arguments for an FMessage instance.
     * Equivalent to decompiled b51.l(args, h60.c) — finds first arg matching FMessage type.
     */
    private static Object findFMessageInArgs(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg != null && FMessageWpp.isFMessage(arg)) {
                return arg;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Bubble Tick Style";
    }
}
