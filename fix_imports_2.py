import sys
import re

filepath = r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\xposed\features\customization\uiadblocker\WhatsAppUiElementHooks.kt"
with open(filepath, 'r', encoding='utf-8') as f:
    text = f.read()

# Collect imports
lines = text.split('\n')
seen_imports = set()
out_lines = []

for line in lines:
    if line.startswith('import '):
        if line in seen_imports:
            continue
        seen_imports.add(line)
    out_lines.append(line)

text = '\n'.join(out_lines)

# Also ensure MethodHookParam without generics
text = re.sub(r'MethodHookParam<\*>', 'MethodHookParam', text)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(text)
