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
    public TagMessage(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Method method = Unobfuscator.loadForwardTagMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(method));
        Class<?> forwardClass = Unobfuscator.loadForwardClassMethod(classLoader);
        logDebug("ForwardClass: " + forwardClass.getName());

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String mode = getHideTagMode();
                if (mode.equals("disabled")) return;

                if (mode.equals("all") || (mode.equals("dialog") && WppCore.getPrivBoolean("forward", false))) {
                    var arg = (long) param.args[0];
                    if (arg == 1) {
                        if (ReflectionUtils.isCalledFromClass(forwardClass)) {
                            param.args[0] = 0L;
                        }
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!getHideTagMode().equals("dialog")) return;

                var intent = (Intent) param.args[0];
                if (intent == null) return;
                var activity = (Activity) param.thisObject;
                var requestCode = (int) param.args[1];
                var options = (Bundle) param.args[2];

                if (intent.getComponent() != null && intent.getComponent().getClassName().contains("ContactPicker") && !intent.getBooleanExtra("bypass_forward", false)) {
                    param.setResult(null);

                    var dialog = new AlertDialogWpp(activity);
                    dialog.setTitle(activity.getString(ResId.string.hide_forward_ask));
                    dialog.setMessage(activity.getString(ResId.string.msg_hide_the_forwarding_label));

                    dialog.setPositiveButton(activity.getString(ResId.string.yes), (d, w) -> {
                        intent.putExtra("bypass_forward", true);
                        WppCore.setPrivBoolean("forward", true);
                        activity.startActivityForResult(intent, requestCode, options);
                    });

                    dialog.setNegativeButton(activity.getString(ResId.string.no), (d, w) -> {
                        intent.putExtra("bypass_forward", true);
                        WppCore.removePrivKey("forward");
                        activity.startActivityForResult(intent, requestCode, options);
                    });


                    dialog.setCancelable(true);
                    dialog.show();
                }
            }
        });

        if (prefs.getBoolean("broadcast_tag", false)) {
            hookBroadcastView();
        }
    }

    private String getHideTagMode() {
        if (prefs.getBoolean("hidetag", false)) {
            return "all";
        }
        return prefs.getString("forward_tag", "disabled");
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
