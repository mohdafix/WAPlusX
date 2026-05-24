package com.wmods.wppenhacer.xposed.bridge;

import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ScopeHook {

    private static Set<XC_MethodHook.Unhook> hook;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if ("android".equals(lpparam.packageName) && "android".equals(lpparam.processName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookService(lpparam);
            } else if ("com.android.providers.settings".equals(lpparam.packageName)) {
                hookSettings(lpparam);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private static void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        Class<?> clsSet = XposedHelpers.findClass("com.android.providers.settings.SettingsProvider", lpparam.classLoader);

        // Bundle call(String method, String arg, Bundle extras)
        Method mCall = clsSet.getMethod("call", String.class, String.class, Bundle.class);
        XposedBridge.hookMethod(mCall, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String method = (String) param.args[0];
                    String arg = (String) param.args[1];
                    if ("WaEnhancer".equals(method)) {
                        if ("getHookBinder".equals(arg)) {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);
                            XposedBridge.log("Wa Enhancer: Trying to allow blocking ");
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            var result = Utils.binderLocalScope(() -> {
                                var uri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider");
                                return context.getContentResolver().call(uri, "getHookBinder", null, null);
                            });
                            param.setResult(result);
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            XposedBridge.log("Wa Enhancer: Bypass Scope using Provider Settings");
                        }
                    }
                } catch (Throwable ex) {
                    XposedBridge.log(ex);
                }
            }
        });
    }

    private static void hookService(XC_LoadPackage.LoadPackageParam lpparam) {
        var serviceClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader);
        var addService = ReflectionUtils.findMethodUsingFilter(serviceClass, method -> method.getName().equals("addService"));
        var hookedService = new AtomicReference<XC_MethodHook.Unhook>();
        var hooked = XposedBridge.hookMethod(addService, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var service = (String) param.args[0];
                if (Objects.equals(service, "package")) {
                    if (hookedService.get() != null) {
                        hookedService.get().unhook();
                    }
                    new Thread(() -> hookScope(param.args[1], lpparam.classLoader)).start();
                }
            }
        });
        hookedService.set(hooked);
    }

    private static void hookScope(Object pms, ClassLoader loader) {
        XposedBridge.log("Hooked visibility Scope");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hook = XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.server.pm.AppsFilterBase", loader), "shouldFilterApplication", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        var snapshot = param.args[0];
                        var callingUid = (int) param.args[1];
                        if (callingUid == 1000) return;
                        var callingApps = Utils.binderLocalScope(() -> {
                            var computerClass = XposedHelpers.findClass("com.android.server.pm.Computer", loader);
                            var getPackagesForUidMethod = ReflectionUtils.findMethodUsingFilter(computerClass, method -> method.getName().equals("getPackagesForUid"));
                            return (String[]) ReflectionUtils.callMethod(getPackagesForUidMethod, snapshot, callingUid);
                        });
                        if (callingApps == null) return;
                        var targetApp = getPackageNameFromPackageSettings(param.args[3]);
                        for (var caller : callingApps) {
                            if ((caller.equals(FeatureLoader.PACKAGE_WPP) || caller.equals(FeatureLoader.PACKAGE_BUSINESS)) && targetApp.equals(BuildConfig.APPLICATION_ID)) {
                                param.setResult(Boolean.FALSE);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log("Error while hooking Android System");
                        XposedBridge.log(e);
                        unhook();
                    }
                }
            });
        } else {
            hook = XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.server.pm.AppsFilter", loader), "shouldFilterApplication", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        var callingUid = (int) param.args[0];
                        if (callingUid == 1000) return;
                        var callingApps = Utils.binderLocalScope(() -> {
                            var getPackagesForUidMethod = ReflectionUtils.findMethodUsingFilter(pms.getClass(), method -> method.getName().equals("getPackagesForUid"));
                            return (String[]) ReflectionUtils.callMethod(getPackagesForUidMethod, pms, callingUid);
                        });
                        if (callingApps == null) return;
                        var targetApp = getPackageNameFromPackageSettings(param.args[2]);
                        for (var caller : callingApps) {
                            if ((caller.equals(FeatureLoader.PACKAGE_WPP) || caller.equals(FeatureLoader.PACKAGE_BUSINESS)) && targetApp.equals(BuildConfig.APPLICATION_ID)) {
                                param.setResult(Boolean.FALSE);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log("Error while hooking Android System");
                        XposedBridge.log(e);
                        unhook();
                    }
                }
            });
        }

    }

    private static void unhook() {
        if (hook != null) {
            for (var unhook : hook) {
                unhook.unhook();
            }
        }
    }

    private static String getPackageNameFromPackageSettings(Object packageSettings) {
        if (packageSettings == null) return "";
        
        // 1. Try getPackageName() method (Android 12/13/14+)
        try {
            Method getPackageNameMethod = ReflectionUtils.findMethodUsingFilterIfExists(
                packageSettings.getClass(),
                m -> "getPackageName".equals(m.getName()) && m.getParameterCount() == 0 && m.getReturnType() == String.class
            );
            if (getPackageNameMethod != null) {
                getPackageNameMethod.setAccessible(true);
                return (String) getPackageNameMethod.invoke(packageSettings);
            }
        } catch (Throwable ignored) {}

        // 2. Try 'name' field (Android 11 PackageSetting)
        try {
            Field nameField = ReflectionUtils.findFieldUsingFilterIfExists(
                packageSettings.getClass(),
                f -> "name".equals(f.getName()) && f.getType() == String.class
            );
            if (nameField != null) {
                nameField.setAccessible(true);
                return (String) nameField.get(packageSettings);
            }
        } catch (Throwable ignored) {}

        // 3. Try 'pkg' field -> getPackageName()
        try {
            Field pkgField = ReflectionUtils.findFieldUsingFilterIfExists(
                packageSettings.getClass(),
                f -> "pkg".equals(f.getName())
            );
            if (pkgField != null) {
                pkgField.setAccessible(true);
                Object pkg = pkgField.get(packageSettings);
                if (pkg != null) {
                    Method getPackageNameMethod = ReflectionUtils.findMethodUsingFilterIfExists(
                        pkg.getClass(),
                        m -> "getPackageName".equals(m.getName()) && m.getParameterCount() == 0 && m.getReturnType() == String.class
                    );
                    if (getPackageNameMethod != null) {
                        getPackageNameMethod.setAccessible(true);
                        return (String) getPackageNameMethod.invoke(pkg);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Fallback: Parse string representation
        try {
            String str = packageSettings.toString();
            int spaceIdx = str.lastIndexOf(' ');
            int slashIdx = str.lastIndexOf('/');
            if (spaceIdx != -1 && slashIdx != -1 && slashIdx > spaceIdx + 1) {
                return str.substring(spaceIdx + 1, slashIdx);
            }
        } catch (Throwable ignored) {}

        return "";
    }

}
