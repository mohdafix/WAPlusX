import os
import re

source = r"C:\Project\Purffectt\Purrfect\core\src\main\kotlin\me\eternal\purrfect\core\whatsapp\WhatsAppUiElementHooks.kt"
dest_dir = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\customization\uiadblocker"

os.makedirs(dest_dir, exist_ok=True)

with open(source, 'r', encoding='utf-8') as f:
    content = f.read()

# Modify package
content = content.replace("package me.eternal.purrfect.core.whatsapp", "package com.wmods.wppenhacer.xposed.features.customization.uiadblocker")

# Remove eternal purrfect imports
content = re.sub(r"import me\.eternal\.purrfect\..*\n", "", content)

# Add our custom imports
content = """import de.robv.android.xposed.XSharedPreferences
import android.content.Intent
import com.wmods.wppenhacer.xposed.utils.Utils
""" + content

# Replace WhatsAppFeatureStateStore.current with our XSharedPreferences reading logic
# Wait, WhatsAppUiElementHooks uses `featureState.hideUiElements`, `featureState.captureUiElements`, `featureState.hiddenUiElementIds`, `featureState.hiddenUiElementSelectors`
# We'll create a local class `FeatureState` that reads from `prefs`.

feature_state_impl = """
    private val featureState = object {
        val hideUiElements: Boolean get() = prefs.getBoolean("hide_ui_elements", false)
        val captureUiElements: Boolean get() = prefs.getBoolean("capture_ui_elements", false)
        val hiddenUiElementIds: String get() = prefs.getString("hidden_ui_element_ids", "") ?: ""
        val hiddenUiElementSelectors: String get() = prefs.getString("hidden_ui_element_selectors", "") ?: ""
    }
"""

content = re.sub(r"private val featureState get\(\) = WhatsAppFeatureStateStore\.current", feature_state_impl, content)

# Replace Constants.MODULE_PACKAGE_NAME with "com.wmods.wppenhacer"
content = content.replace("Constants.MODULE_PACKAGE_NAME", '"com.wmods.wppenhacer"')
content = content.replace("Constants.WHATSAPP_UI_ELEMENT_CAPTURED_ACTION", '"com.wmods.wppenhacer.UI_ELEMENT_CAPTURED"')
content = content.replace("Constants.WHATSAPP_UI_ELEMENT_CAPTURED_VALUE_EXTRA", '"captured_value"')
content = content.replace("Constants.WHATSAPP_UI_ELEMENT_CAPTURED_IS_SELECTOR_EXTRA", '"is_selector"')

# Replace CoreLogger.xposedLog and WhatsAppAppLogWriter.info with XposedBridge.log
content = content.replace("CoreLogger.xposedLog(message, TAG)", "")
content = content.replace("WhatsAppAppLogWriter.info(androidContext, TAG, message)", "")
content = content.replace("WhatsAppAppLogWriter.info(null, TAG, message)", "")

# Add `prefs` to constructor
content = content.replace(
    "class WhatsAppUiElementHooks(",
    "class WhatsAppUiElementHooks(\n    private val prefs: XSharedPreferences,"
)

# There is a call to `WhatsAppFeatureStateStore.update(updated)` which we can't do natively here.
# We'll remove that call, because the BroadcastReceiver in the WAE app will handle saving it to SharedPreferences!
content = re.sub(r"val updated = if \(isSelector\).*?WhatsAppFeatureStateStore\.update\(updated\)", "// Handled by BroadcastReceiver", content, flags=re.DOTALL)

with open(os.path.join(dest_dir, "WhatsAppUiElementHooks.kt"), 'w', encoding='utf-8') as f:
    f.write(content)
