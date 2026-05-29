package com.wmods.wppenhacer.xposed.features.others;

import static com.wmods.wppenhacer.xposed.features.general.LiteMode.REQUEST_FOLDER;
import static com.wmods.wppenhacer.xposed.features.general.LiteMode.getDownloadsUri;
import static com.wmods.wppenhacer.xposed.features.general.LiteMode.processDownloadResult;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.preference.ContactPickerPreference;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class ActivityController extends Feature {

    private static String Key;

    public ActivityController(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        Class<?> statusDistribution = Unobfuscator.loadStatusDistributionClass(classLoader);

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                var intent = activity.getIntent();
                if (intent == null) return;
                
                if (intent.getBooleanExtra("download_mode", false)) {
                    if (activity.getClass().getName().endsWith("SettingsNotifications")) {
                        downloadController(activity, intent);
                    }
                } else if (intent.getBooleanExtra("contact_mode", false)) {
                    if (activity.getClass().getName().endsWith("SettingsNotifications")) {
                        try {
                            contactController(intent, activity);
                        } catch (Exception e) {
                            de.robv.android.xposed.XposedBridge.log(e);
                        }
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                if (!activity.getClass().getName().endsWith("SettingsNotifications")) return;
                var id = (int) param.args[0];
                Intent intent = (Intent) param.args[2];
                if (id == REQUEST_FOLDER && (int) param.args[1] == Activity.RESULT_OK) {
                    var uriStr = processDownloadResult(activity, intent);
                    Intent intent2 = new Intent();
                    intent2.putExtra("path", uriStr);
                    intent2.putExtra("key", Key);
                    activity.setResult(Activity.RESULT_OK, intent2);
                    activity.finish();
                } else if (id == ContactPickerPreference.REQUEST_CONTACT_PICKER) {
                    if (intent != null) {
                        processResultContact(intent, activity);
                    } else {
                        activity.setResult(Activity.RESULT_CANCELED);
                    }
                    activity.finish();
                }
            }
        });

    }

    private void downloadController(Activity activity, Intent intent2) {
        Key = intent2.getStringExtra("key");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDownloadsUri());
        activity.startActivityForResult(intent, REQUEST_FOLDER);
    }

    private static void contactController(Intent intent, Activity activity) throws Exception {
        Key = intent.getStringExtra("key");
        var contacts = intent.getStringArrayListExtra("contacts");
        var pickerIntent = com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher.createAboutPickerIntent(activity, activity.getPackageName(), Key == null ? "" : Key, contacts);
        if ("schedule_message_contacts".equals(Key)) {
            pickerIntent.putExtra("show_groups", true);
        }
        activity.startActivityForResult(pickerIntent, ContactPickerPreference.REQUEST_CONTACT_PICKER);
    }

    private static void processResultContact(Intent intent, Activity activity) {
        if (!intent.hasExtra("key") && Key != null) {
            intent.putExtra("key", Key);
        }
        if (!intent.hasExtra("contacts")) {
            intent.putStringArrayListExtra("contacts", new ArrayList<>());
        }
        if (!intent.hasExtra("picker_contacts")) {
            intent.putExtra("picker_contacts", new ArrayList<com.wmods.wppenhacer.model.ContactPickerResult>());
        }
        activity.setResult(Activity.RESULT_OK, intent);
    }



    @NonNull
    @Override
    public String getPluginName() {
        return "Activity Controller";
    }

}
