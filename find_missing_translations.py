import os
import re

wae_res_dir = r"C:\Project\WAE\app\src\main\res"
langs = ["ar", "de", "es", "fr", "id", "in", "it", "iw", "pt", "ru", "tr", "zh"]

with open(os.path.join(wae_res_dir, "values", "strings.xml"), "r", encoding="utf-8") as f:
    en_content = f.read()

en_keys = re.findall(r'<string name="([^"]+)">', en_content)

missing_keys_by_lang = {}

for lang in langs:
    target_file = os.path.join(wae_res_dir, f"values-{lang}", "strings.xml")
    if not os.path.exists(target_file): continue
    
    with open(target_file, "r", encoding="utf-8") as f:
        lang_content = f.read()
        
    lang_keys = set(re.findall(r'<string name="([^"]+)">', lang_content))
    missing = [k for k in en_keys if k not in lang_keys]
    if missing:
        missing_keys_by_lang[lang] = missing

for lang, keys in missing_keys_by_lang.items():
    print(f"{lang} is missing {len(keys)} keys")
    if len(keys) < 20:
        print(f"  {keys}")
