package com.wmods.wppenhacer.xposed.features.others;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class PropDebugger extends Feature {

    private final Map<Integer, Boolean> dynamicPropsBoolean = new HashMap<>();
    private final Map<Integer, Integer> dynamicPropsInteger = new HashMap<>();
    private final Map<Integer, String> dynamicPropsString = new HashMap<>();

    public PropDebugger(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
        loadDynamicProps();
    }

    private void loadDynamicProps() {
        try {
            String json = prefs.getString("prop_overrides", "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int id = obj.getInt("id");
                String type = obj.getString("type");
                Object value = obj.get("value");

                if ("boolean".equals(type)) {
                    dynamicPropsBoolean.put(id, (Boolean) value);
                } else if ("int".equals(type) || "integer".equals(type)) {
                    dynamicPropsInteger.put(id, (Integer) value);
                } else if ("string".equals(type)) {
                    dynamicPropsString.put(id, (String) value);
                }
            }
        } catch (Exception e) {
            logDebug("PropDebugger: Error loading dynamic props: " + e.getMessage());
        }
    }

    @Override
    public void doHook() throws Exception {
        hookBooleanProps();
        hookIntegerProps();
        hookStringProps();
    }

    private void hookBooleanProps() throws Exception {
        var method = Unobfuscator.loadPropsBooleanMethod(classLoader);
        if (method != null) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0) return;
                    
                    Integer id = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer) {
                            id = (Integer) arg;
                            break;
                        }
                    }
                    
                    if (id != null && dynamicPropsBoolean.containsKey(id)) {
                        param.setResult(dynamicPropsBoolean.get(id));
                        if (!Utils.isSpam("PropDebugger_Bool_" + id, 60000)) {
                            XposedBridge.log("PropDebugger: Overriding Boolean Prop " + id + " -> " + dynamicPropsBoolean.get(id));
                        }
                    }
                }
            });
        }
    }

    private void hookIntegerProps() throws Exception {
        var method = Unobfuscator.loadPropsIntegerMethod(classLoader);
        if (method != null) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0) return;

                    Integer id = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer) {
                            id = (Integer) arg;
                            break;
                        }
                    }

                    if (id != null && dynamicPropsInteger.containsKey(id)) {
                        param.setResult(dynamicPropsInteger.get(id));
                        if (!Utils.isSpam("PropDebugger_Int_" + id, 60000)) {
                            XposedBridge.log("PropDebugger: Overriding Integer Prop " + id + " -> " + dynamicPropsInteger.get(id));
                        }
                    }
                }
            });
        }
    }

    private void hookStringProps() throws Exception {
        var method = Unobfuscator.loadPropsStringMethod(classLoader);
        if (method != null) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0) return;

                    Integer id = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer) {
                            id = (Integer) arg;
                            break;
                        }
                    }

                    if (id != null && dynamicPropsString.containsKey(id)) {
                        param.setResult(dynamicPropsString.get(id));
                        if (!Utils.isSpam("PropDebugger_String_" + id, 60000)) {
                            XposedBridge.log("PropDebugger: Overriding String Prop " + id + " -> " + dynamicPropsString.get(id));
                        }
                    }
                }
            });
        }
    }

    @Override
    public String getPluginName() {
        return "PropDebugger";
    }
}
