import sys

filepath = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\customization\uiadblocker\WhatsAppUiElementHooks.kt"
with open(filepath, 'r', encoding='utf-8') as f:
    text = f.read()

# find package
import re
match = re.search(r'package com\.wmods\.wppenhacer\.xposed\.features\.customization\.uiadblocker\n', text)
if match:
    text = text.replace(match.group(0), "")
    text = "package com.wmods.wppenhacer.xposed.features.customization.uiadblocker\n\n" + text

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(text)
