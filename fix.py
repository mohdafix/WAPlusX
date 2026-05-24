import os
import re

def patch(path, old, new):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c.replace(old, new))

p1 = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\others\SettingsInjector.java"
patch(p1, "SharedPreferences preferences", "de.robv.android.xposed.XSharedPreferences preferences")
patch(p1, "import android.content.SharedPreferences;", "")
patch(p1, "public void doHook()", "public String getPluginName() { return \"SettingsInjector\"; }\n\n    @Override\n    public void doHook()")
patch(p1, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings, \"WaEnhancerX Settings\")", "\"WaEnhancer Settings\"")
patch(p1, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings)", "\"WaEnhancer Settings\"")
patch(p1, "R.string.waenhancer_settings_desc", "\"Enhance your WhatsApp experience\"")
patch(p1, "Unobfuscator.loadSettingsActivityClass(classLoader)", "null")
patch(p1, "getSafeString(", "prefs.getString(")
patch(p1, "Utils.openModule(activity)", "activity.startActivity(new android.content.Intent().setClassName(\"com.wmods.wppenhacer\", \"com.wmods.wppenhacer.xposed.features.others.EmbeddedSettingsActivity\"))")

p2 = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\others\EmbeddedMainFragment.java"
patch(p2, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.general)", "\"General\"")
patch(p2, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.privacy)", "\"Privacy\"")
patch(p2, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.title_home)", "\"Home\"")
patch(p2, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.media)", "\"Media\"")
patch(p2, "com.wmods.wppenhacer.xposed.core.FeatureLoader.getModuleString(R.string.perso)", "\"Personalization\"")
