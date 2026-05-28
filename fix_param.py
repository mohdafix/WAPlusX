import sys

filepath = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\customization\uiadblocker\WhatsAppUiElementHooks.kt"
with open(filepath, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace("MethodHookParam<*>", "MethodHookParam")

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(text)
