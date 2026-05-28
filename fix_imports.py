import sys

filepath = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\customization\uiadblocker\WhatsAppUiElementHooks.kt"
with open(filepath, 'r', encoding='utf-8') as f:
    text = f.read()

# Remove second Intent import
text = text.replace("import android.content.Intent\n", "", 1)
# Add ClipData back
if "import android.content.ClipData" not in text:
    text = text.replace("import android.content.ClipboardManager", "import android.content.ClipData\nimport android.content.ClipboardManager")

# Fix TAG
text = text.replace("WhatsAppChannelHooks.TAG", '"WhatsAppUiElementHooks"')

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(text)
