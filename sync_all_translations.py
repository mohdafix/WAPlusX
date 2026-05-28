import os
import re

wae_res_dir = r"C:\Project\WAE\app\src\main\res"
latest_res_dir = r"C:\Users\user\Downloads\wae-latest26\resources\res"

langs = ["ar", "de", "es", "fr", "id", "in", "it", "iw", "pt", "ru", "tr", "zh"]

with open(os.path.join(wae_res_dir, "values", "strings.xml"), "r", encoding="utf-8") as f:
    en_content = f.read()

# Get all keys in English file
en_keys = re.findall(r'<string name="([^"]+)">', en_content)

for lang in langs:
    target_file = os.path.join(wae_res_dir, f"values-{lang}", "strings.xml")
    source_file = os.path.join(latest_res_dir, f"values-{lang}", "strings.xml")
    
    if not os.path.exists(target_file):
        continue
    
    if not os.path.exists(source_file):
        if lang == "zh": source_file = os.path.join(latest_res_dir, "values-zh-rCN", "strings.xml")
        elif lang == "pt": source_file = os.path.join(latest_res_dir, "values-pt-rBR", "strings.xml")
        elif lang == "id": source_file = os.path.join(latest_res_dir, "values-in", "strings.xml")
        
    if not os.path.exists(source_file):
        continue

    with open(source_file, "r", encoding="utf-8") as f:
        source_content = f.read()

    with open(target_file, "r", encoding="utf-8") as f:
        target_content = f.read()

    # Find keys missing in target but present in source
    target_keys = set(re.findall(r'<string name="([^"]+)">', target_content))
    missing_keys = [k for k in en_keys if k not in target_keys]
    
    appended_strings = []
    
    for key in missing_keys:
        match = re.search(f'<string name="{key}">.*?</string>', source_content)
        if match:
            appended_strings.append("    " + match.group(0))
            
    if appended_strings:
        replacement = "\n" + "\n".join(appended_strings) + "\n</resources>"
        new_content = target_content.replace("</resources>", replacement)
        with open(target_file, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"Added {len(appended_strings)} strings to {lang}")
    else:
        print(f"No new strings found in reference for {lang}")
