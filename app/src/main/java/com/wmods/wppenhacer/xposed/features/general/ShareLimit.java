package com.wmods.wppenhacer.xposed.features.general;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ShareLimit extends Feature {
    public ShareLimit(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("removeforwardlimit", false)) return;
        var shareLimitMethod = Unobfuscator.loadShareLimitMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(shareLimitMethod));
        var shareItemField = Unobfuscator.loadShareMapItemField(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(shareItemField));

        XposedBridge.hookMethod(
                shareLimitMethod,
                new XC_MethodHook() {
                    private java.util.HashMap<Object, Object> fakeMap;
                    private java.util.Map<Object, Object> mMap;

                    /**
                     * @noinspection unchecked
                     */
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        fakeMap = new java.util.HashMap<>();
                        mMap = (java.util.Map<Object, Object>) shareItemField.get(param.thisObject);
                        com.wmods.wppenhacer.xposed.utils.ReflectionUtils.setFinalField(shareItemField, param.thisObject, fakeMap);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mMap.putAll(fakeMap);
                        com.wmods.wppenhacer.xposed.utils.ReflectionUtils.setFinalField(shareItemField, param.thisObject, mMap);
                        fakeMap.clear();
                    }
                });


    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Share Limit";
    }
}
