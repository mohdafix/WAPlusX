package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Swipe left on a conversation message bubble to trigger an action (info, edit, delete).
 */
public class SwipeConversation extends Feature {
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_DISABLED = "disabled";
    private static final String ACTION_EDIT = "edit";
    private static final String ACTION_INFO = "info";

    private static final String PREF_SWIPE_CONVERSATION_ACTION = "swipe_conversation_action";

    private Method checkIsEditableMethod;
    private Class<?> fMessageUtilClass;
    private Method createDeleteDialog;
    private Method showDialogMethod;
    private Class<?> messageDetailsActivityClass;
    private Class<?> editMessageActivityClass;

    public SwipeConversation(ClassLoader classLoader, XSharedPreferences xSharedPreferences) {
        super(classLoader, xSharedPreferences);
    }

    private final class ConversationSwipeHook extends XC_MethodHook {
        private final HashMap<String, Field> swipeFields;
        private Field downXField;
        private Field slopField;
        private Field bubbleViewField;
        private Field stateField;
        private boolean initialized;

        private ConversationSwipeHook(HashMap<String, Field> swipeFields) {
            this.swipeFields = swipeFields;
        }

        private boolean initialize() {
            if (initialized) {
                return true;
            }
            try {
                downXField = swipeFields.get("downX");
                slopField = swipeFields.get("slop");
                bubbleViewField = swipeFields.get("bubbleView");
                stateField = swipeFields.get("state");

                if (downXField != null) downXField.setAccessible(true);
                if (slopField != null) slopField.setAccessible(true);
                if (bubbleViewField != null) bubbleViewField.setAccessible(true);
                if (stateField != null) stateField.setAccessible(true);

                initialized = downXField != null && slopField != null && bubbleViewField != null;
                return initialized;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (!initialize()) return;

                MotionEvent event = (MotionEvent) param.args[0];
                Object obj = param.thisObject;

                int downX = downXField.getInt(obj);
                int slop = slopField.getInt(obj);
                int deltaX = ((int) event.getX()) - downX;

                if (deltaX >= -slop) return;

                View view = (View) obj;
                int action = event.getAction();

                if (action == MotionEvent.ACTION_MOVE) {
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    view.setSelected(false);
                    view.cancelLongPress();
                    if (stateField != null) {
                        stateField.setInt(obj, 1);
                    }
                }

                int threshold = view.getWidth() / 6;
                int distance = Math.max(0, Math.abs(deltaX) - slop);
                float overscroll = (float) Math.max(0, distance - threshold);
                float f = (float) threshold;
                float translation = -((overscroll / (((0.75f * overscroll) / f) + 1.0f))
                        + (float) Math.min(distance, threshold));

                View bubbleView = (View) bubbleViewField.get(obj);
                if (bubbleView != null) {
                    bubbleView.setTranslationX(translation);
                }
                view.invalidate();

                if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
                    param.setResult(Boolean.TRUE);
                    return;
                }

                if (Math.abs(translation) > f) {
                    Object fMessage = XposedHelpers.callMethod(view, "getFMessage");
                    if (fMessage != null) {
                        FMessageWpp msg = new FMessageWpp(fMessage);
                        FMessageWpp.Key key = msg.getKey();
                        if (key != null && key.isFromMe) {
                            performAction(msg, key);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void performAction(FMessageWpp message, FMessageWpp.Key key) {
        String action = prefs.getString(PREF_SWIPE_CONVERSATION_ACTION, ACTION_DISABLED);
        if (action == null || ACTION_DISABLED.equals(action)) return;

        Activity activity = WppCore.getCurrentActivity();
        if (activity == null) return;

        try {
            switch (action) {
                case ACTION_INFO:
                    openMessageInfo(activity, key);
                    break;
                case ACTION_EDIT:
                    openEditMessage(activity, message, key);
                    break;
                case ACTION_DELETE:
                    openDeleteDialog(activity, message, key);
                    break;
            }
        } catch (Throwable t) {
            log(t);
        }
    }

    private void openMessageInfo(Activity activity, FMessageWpp.Key key) {
        try {
            if (messageDetailsActivityClass == null) return;
            Intent intent = new Intent(activity, messageDetailsActivityClass);
            intent.putExtra("key_remote_jid", key.remoteJid.getUserRawString());
            intent.putExtra("key_id", key.messageID);
            activity.startActivity(intent);
        } catch (Throwable t) {
            log(t);
        }
    }

    private void openEditMessage(Activity activity, FMessageWpp message, FMessageWpp.Key key) {
        try {
            if (checkIsEditableMethod != null && fMessageUtilClass != null) {
                Object util = fMessageUtilClass.newInstance();
                boolean editable = (Boolean) checkIsEditableMethod.invoke(util, message.getObject());
                if (!editable) {
                    Utils.showToast(Utils.getApplication().getString(
                            com.wmods.wppenhacer.R.string.swipe_conversation_message_not_editable), 1);
                    return;
                }
            }

            if (editMessageActivityClass == null) return;

            Intent intent = new Intent(activity, editMessageActivityClass);
            intent.putExtra("fMessageKeyId", key.messageID);
            intent.putExtra("fMessageKeyFromMe", key.isFromMe);
            intent.putExtra("fMessageKeyJid", key.remoteJid.getUserRawString());
            activity.startActivity(intent);
        } catch (Throwable t) {
            log(t);
        }
    }

    private void openDeleteDialog(Activity activity, FMessageWpp message, FMessageWpp.Key key) {
        try {
            if (createDeleteDialog == null) return;

            Object fm = XposedHelpers.callMethod(activity, "getSupportFragmentManager");
            Object dialog;
            try {
                dialog = createDeleteDialog.invoke(null,
                        key.remoteJid.userJid, Collections.singletonList(message.getObject()));
            } catch (Throwable e) {
                dialog = createDeleteDialog.invoke(null,
                        Collections.singletonList(message.getObject()), key.remoteJid.userJid);
            }
            if (showDialogMethod != null) {
                showDialogMethod.invoke(dialog, fm, null);
            } else {
                XposedHelpers.callMethod(dialog, "show", fm, null);
            }
        } catch (Throwable t) {
            log(t);
        }
    }

    @Override
    public void doHook() {
        try {
            HashMap<String, Field> fields = Unobfuscator.loadConversationSwipeFields(classLoader);
            Method swipeMethod = Unobfuscator.loadConversationSwipeMethod(classLoader);

            try { checkIsEditableMethod = Unobfuscator.loadCheckIsEditableMethod(classLoader); } catch (Throwable ignored) {}
            try { fMessageUtilClass = Unobfuscator.loadClassFMessageUtil(classLoader); } catch (Throwable ignored) {}
            try { createDeleteDialog = Unobfuscator.loadCreateDeleteDialog(classLoader); } catch (Throwable ignored) {}
            try { showDialogMethod = Unobfuscator.loadShowDialogMethod(classLoader); } catch (Throwable ignored) {}

            try { messageDetailsActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "MessageDetailsActivity"); } catch (Throwable ignored) {}
            try { editMessageActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "EditMessageActivity"); } catch (Throwable ignored) {}

            Method touchMethod = swipeMethod.getDeclaringClass()
                    .getDeclaredMethod("onTouchEvent", new Class[]{MotionEvent.class});
            XposedBridge.hookMethod(touchMethod, new ConversationSwipeHook(fields));
        } catch (Throwable t) {
            log(t);
        }
    }

    @Override
    public String getPluginName() {
        return "Swipe Conversation";
    }
}
