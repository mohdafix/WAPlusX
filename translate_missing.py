import os
import re
import time
from deep_translator import GoogleTranslator

wae_res_dir = r"C:\Project\WAE\app\src\main\res"

lang_map = {
    "ar": "ar", "de": "de", "es": "es", "fr": "fr",
    "id": "id", "in": "id", "it": "it", "iw": "iw",
    "pt": "pt", "ru": "ru", "tr": "tr", "zh": "zh-CN"
}

with open(os.path.join(wae_res_dir, "values", "strings.xml"), "r", encoding="utf-8") as f:
    en_content = f.read()

en_keys = re.findall(r'<string name="([^"]+)">([^<]+)</string>', en_content)
en_dict = {k: v for k, v in en_keys}

for lang_dir, glang in lang_map.items():
    target_file = os.path.join(wae_res_dir, f"values-{lang_dir}", "strings.xml")
    if not os.path.exists(target_file):
        continue
        
    with open(target_file, "r", encoding="utf-8") as f:
        lang_content = f.read()
        
    lang_keys = set(re.findall(r'<string name="([^"]+)">', lang_content))
    missing = [k for k in en_dict.keys() if k not in lang_keys]
    
    if not missing:
        continue
        
    appended_strings = []
    print(f"Translating {len(missing)} strings for {lang_dir} ({glang})...")
    
    translator = GoogleTranslator(source='en', target=glang)
    
    for k in missing:
        v = en_dict[k].replace("\\'", "'").replace("\\n", "\n")
        try:
            # We translate one by one or in a batch. One by one is safer for deep_translator to avoid limits.
            t = translator.translate(v)
            if not t: t = v
            # Re-escape
            translated_text = t.replace("'", "\\'").replace("\n", "\\n")
            translated_text = translated_text.replace("% s", "%s")
            appended_strings.append(f'    <string name="{k}">{translated_text}</string>')
        except Exception as e:
            print(f"Error on {k}: {e}")
            
    if appended_strings:
        replacement = "\n" + "\n".join(appended_strings) + "\n</resources>"
        new_content = lang_content.replace("</resources>", replacement)
        with open(target_file, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"-> Added {len(appended_strings)} strings to {lang_dir}")
