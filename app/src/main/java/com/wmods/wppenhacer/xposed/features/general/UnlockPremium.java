package com.wmods.wppenhacer.xposed.features.general;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class UnlockPremium extends Feature {

    private static final Map<Integer, Boolean> PREMIUM_BOOLS = new HashMap<>();
    private static final Map<Integer, Integer> PREMIUM_INTS = new HashMap<>();

    static {
        // Core Premium / Aura keys
        PREMIUM_BOOLS.put(23270, true);
        PREMIUM_BOOLS.put(23272, true);
        PREMIUM_BOOLS.put(28345, false); 
        PREMIUM_BOOLS.put(27137, true);
        PREMIUM_BOOLS.put(23271, true);
        PREMIUM_BOOLS.put(24823, true);
        PREMIUM_BOOLS.put(23277, true);
        PREMIUM_BOOLS.put(24047, true);
        PREMIUM_BOOLS.put(24800, false);
        PREMIUM_BOOLS.put(23274, true);
        PREMIUM_BOOLS.put(27798, true);
        PREMIUM_BOOLS.put(27210, true);
        PREMIUM_BOOLS.put(26086, true);
        
        // New keys from original repository (WhatsApp 2.26.18.x+)
        PREMIUM_BOOLS.put(11528, false);
        PREMIUM_BOOLS.put(2358, false);
        PREMIUM_BOOLS.put(7769, false);
        PREMIUM_BOOLS.put(9286, false);
        PREMIUM_BOOLS.put(3354, true);
        PREMIUM_BOOLS.put(5418, true);
        PREMIUM_BOOLS.put(11824, false);
        PREMIUM_BOOLS.put(6481, false);
        PREMIUM_BOOLS.put(13591, true);
        PREMIUM_BOOLS.put(10024, true);
        PREMIUM_BOOLS.put(6798, true);
        PREMIUM_BOOLS.put(7589, true);
        PREMIUM_BOOLS.put(6972, false);
        PREMIUM_BOOLS.put(5625, true);
        PREMIUM_BOOLS.put(8643, true);
        PREMIUM_BOOLS.put(8607, true);
        PREMIUM_BOOLS.put(9141, true);
        PREMIUM_BOOLS.put(8925, true);
        PREMIUM_BOOLS.put(13932, true);
        PREMIUM_BOOLS.put(13278, true);
        PREMIUM_BOOLS.put(18511, false);
        PREMIUM_BOOLS.put(28192, true);
        PREMIUM_BOOLS.put(10380, false);
        PREMIUM_BOOLS.put(13497, true);
        PREMIUM_BOOLS.put(13596, true);
        PREMIUM_BOOLS.put(8841, true);
        PREMIUM_BOOLS.put(14143, true);
        PREMIUM_BOOLS.put(11490, true);
        PREMIUM_BOOLS.put(11491, true);
        PREMIUM_BOOLS.put(13402, true);
        PREMIUM_BOOLS.put(13002, true);
        PREMIUM_BOOLS.put(13003, true);
        PREMIUM_BOOLS.put(6285, true);
        PREMIUM_BOOLS.put(14306, false);
        PREMIUM_BOOLS.put(15345, true);
        PREMIUM_BOOLS.put(13408, false);

        // Integer keys
        PREMIUM_INTS.put(25543, 16767);
        PREMIUM_INTS.put(3657, 90);
        PREMIUM_INTS.put(8135, 2);
        PREMIUM_INTS.put(9973, 1);
        PREMIUM_INTS.put(3877, 2);
    }

    public UnlockPremium(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("unlock_premium", false)) {
            return;
        }

        Method propsBooleanMethod = Unobfuscator.loadPropsBooleanMethod(classLoader);
        Method propsIntegerMethod = Unobfuscator.loadPropsIntegerMethod(classLoader);

        if (propsBooleanMethod != null) {
            XposedBridge.hookMethod(propsBooleanMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null) return;
                    
                    Integer key = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer) {
                            key = (Integer) arg;
                            break;
                        } else if (arg instanceof android.util.Pair) {
                            android.util.Pair<?, ?> pair = (android.util.Pair<?, ?>) arg;
                            if (pair.second instanceof Integer) {
                                key = (Integer) pair.second;
                                break;
                            }
                        }
                    }

                    if (key != null) {
                        Boolean val = PREMIUM_BOOLS.get(key);
                        if (val != null) {
                            param.setResult(val);
                            if (!Utils.isSpam("PremiumBool_" + key, 60000)) {
                                log("Overriding Boolean Key " + key + " -> " + val);
                            }
                        }
                    }
                }
            });
        }

        if (propsIntegerMethod != null) {
            XposedBridge.hookMethod(propsIntegerMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null) return;
                    
                    Integer key = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer) {
                            key = (Integer) arg;
                            break;
                        } else if (arg instanceof android.util.Pair) {
                            android.util.Pair<?, ?> pair = (android.util.Pair<?, ?>) arg;
                            if (pair.second instanceof Integer) {
                                key = (Integer) pair.second;
                                break;
                            }
                        }
                    }

                    if (key != null) {
                        Integer val = PREMIUM_INTS.get(key);
                        if (val != null) {
                            param.setResult(val);
                            if (!Utils.isSpam("PremiumInt_" + key, 60000)) {
                                log("Overriding Integer Key " + key + " -> " + val);
                            }
                        }
                    }
                }
            });
        }

        XposedBridge.log("[UnlockPremium] Activated premium feature hooks.");
    }

    @Override
    public String getPluginName() {
        return "Unlock Premium";
    }
}
