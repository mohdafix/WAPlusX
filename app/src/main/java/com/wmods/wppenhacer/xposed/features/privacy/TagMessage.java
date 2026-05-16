package com.wmods.wppenhacer.xposed.features.privacy;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.ResId;

public class TagMessage extends Feature {
    private static final java.util.concurrent.atomic.AtomicBoolean pendingForwardHide = new java.util.concurrent.atomic.AtomicBoolean(false);

    public TagMessage(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    private String getHideTagMode() {
        if (prefs.contains("forward_tag")) {
            return prefs.getString("forward_tag", "disabled");
        }
        if (prefs.getBoolean("hidetag", false)) {
            return "all";
        }
        return "disabled";
    }

    @Override
    public void doHook() throws Exception {
        String mode = getHideTagMode();

        if ("dialog".equals(mode)) {
            hookStartActivityForResult();
        }

        Method loadForwardTagMethod = Unobfuscator.loadForwardTagMethod(this.classLoader);
        final Class<?> loadForwardClassMethod = Unobfuscator.loadForwardClassMethod(this.classLoader);

        XposedBridge.hookMethod(loadForwardTagMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (((Long) param.args[0]).longValue() == 1
                        && ReflectionUtils.isCalledFromClass(loadForwardClassMethod)) {
                    String mode = getHideTagMode();
                    if ("all".equals(mode)) {
                        param.args[0] = 0L;
                    } else if ("dialog".equals(mode) && pendingForwardHide.get()) {
                        param.args[0] = 0L;
                    }
                }
            }
        });

        if (this.prefs.getBoolean("broadcast_tag", false)) {
            hookBroadcastView();
        }
    }

    private void handleForwardDialog(XC_MethodHook.MethodHookParam param) {
        Intent intent = (Intent) param.args[0];
        if (intent == null) return;

        Bundle extras = intent.getExtras();
        if (extras == null || extras.getBoolean("bypass_forward", false)
                || !extras.getBoolean("forward", false)) return;

        param.setResult(null);
        Activity activity = (Activity) param.thisObject;
        int requestCode = (int) param.args[1];
        Bundle options = param.args.length > 2 ? (Bundle) param.args[2] : null;

        pendingForwardHide.set(false);

        AlertDialogWpp dialog = new AlertDialogWpp(activity);
        if (ResId.string.msg_hide_the_forwarding_label != 0) {
            dialog.setMessage(activity.getString(ResId.string.msg_hide_the_forwarding_label));
        } else {
            dialog.setMessage("Do you want to hide the forwarding label?");
        }

        dialog.setPositiveButton(ResId.string.yes != 0 ? ResId.string.yes : android.R.string.yes, (d, w) -> {
            intent.putExtra("bypass_forward", true);
            pendingForwardHide.set(true);
            activity.startActivityForResult(intent, requestCode, options);
        });

        dialog.setNegativeButton(ResId.string.no != 0 ? ResId.string.no : android.R.string.no, (d, w) -> {
            intent.putExtra("bypass_forward", true);
            pendingForwardHide.set(false);
            activity.startActivityForResult(intent, requestCode, options);
        });

        dialog.show();
    }

    private void hookStartActivityForResult() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleForwardDialog(param);
            }
        };
        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, hook);
        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, hook);
    }

    private void hookBroadcastView() throws Exception {

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup view, int position, View convertView) {
                if (fMessage.getKey().isFromMe) return;
                var dateTextView = (TextView) view.findViewById(Utils.getID("date", "id"));
                if (dateTextView == null) return;
                var dateWrapper = (ViewGroup) dateTextView.getParent();
                int id = Utils.getID("broadcast_icon", "id");
                View res = dateWrapper.findViewById(id);
                if (fMessage.isBroadcast() && res == null) {
                    var broadcast = new ImageView(dateWrapper.getContext());
                    broadcast.setId(id);
                    broadcast.setImageDrawable(DesignUtils.getDrawableByName("broadcast_status_icon"));
                    dateWrapper.addView(broadcast, 0);
                } else if (res != null) {
                    dateWrapper.removeView(res);
                }
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Tag Message";
    }
}
