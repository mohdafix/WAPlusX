import os
import re

path = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\others\EmbeddedBasePreferenceFragment.java"
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

c = re.sub(r'@Override\s+public void onDisplayPreferenceDialog.*?super\.onDisplayPreferenceDialog\(preference\);\s+\}', '', c, flags=re.DOTALL)
c = c.replace('import com.wmods.wppenhacer.ui.helpers.BottomSheetHelper;', '')
c = c.replace('R.string.new_ui_group_filter_unsupported_sum', '\"Not supported on this UI version\"')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

path_inj = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\others\SettingsInjector.java"
with open(path_inj, 'r', encoding='utf-8') as f:
    c = f.read()

c = re.sub(r'com\.wmods\.wppenhacer\.xposed\.core\.FeatureLoader\.getModuleString\(\s*R\.string\.waenhancer_settings_desc\s*,\s*"[^"]*"\s*\)', '"Enhance your WhatsApp experience"', c)
c = re.sub(r'com\.wmods\.wppenhacer\.xposed\.core\.FeatureLoader\.getModuleString\(\s*R\.string\.waenhancer_settings_desc\s*\)', '"Enhance your WhatsApp experience"', c)

with open(path_inj, 'w', encoding='utf-8') as f:
    f.write(c)
