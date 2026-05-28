import os
import re

wae_res_dir = r"C:\Project\WAE\app\src\main\res"

with open(os.path.join(wae_res_dir, "values", "strings.xml"), "r", encoding="utf-8") as f:
    en_content = f.read()

en_keys = re.findall(r'<string name="([^"]+)">([^<]+)</string>', en_content)
en_dict = {k: v for k, v in en_keys}

target_file = os.path.join(wae_res_dir, "values-ar", "strings.xml")
with open(target_file, "r", encoding="utf-8") as f:
    lang_content = f.read()
    
lang_keys = set(re.findall(r'<string name="([^"]+)">', lang_content))

missing = [k for k in en_dict.keys() if k not in lang_keys]
with open(r"C:\Project\WAE\missing.txt", "w", encoding="utf-8") as f:
    for k in missing:
        f.write(f'{k}: {en_dict[k]}\n')
