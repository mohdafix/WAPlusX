import os
import re

wae_res_dir = r"C:\Project\WAE\app\src\main\res"
latest_res_dir = r"C:\Users\user\Downloads\wae-latest26\resources\res"

langs = ["ar", "de", "es", "fr", "id", "in", "it", "iw", "pt", "ru", "tr", "zh"]

# Get English keys
with open(os.path.join(wae_res_dir, "values", "strings.xml"), "r", encoding="utf-8") as f:
    en_content = f.read()

# We only want to sync "schedule_" keys
keys_to_sync = re.findall(r'<string name="(schedule_[^"]+)">', en_content)

for lang in langs:
    target_file = os.path.join(wae_res_dir, f"values-{lang}", "strings.xml")
    source_file = os.path.join(latest_res_dir, f"values-{lang}", "strings.xml")
    
    if not os.path.exists(target_file):
        print(f"Skipping {lang}: target file does not exist")
        continue
    
    # Chinese might be zh-rCN in latest, Portuguese might be pt-rBR
    if not os.path.exists(source_file):
        if lang == "zh": source_file = os.path.join(latest_res_dir, "values-zh-rCN", "strings.xml")
        elif lang == "pt": source_file = os.path.join(latest_res_dir, "values-pt-rBR", "strings.xml")
        elif lang == "id": source_file = os.path.join(latest_res_dir, "values-in", "strings.xml")
        
    if not os.path.exists(source_file):
        print(f"Skipping {lang}: source file does not exist")
        continue

    with open(source_file, "r", encoding="utf-8") as f:
        source_content = f.read()

    with open(target_file, "r", encoding="utf-8") as f:
        target_content = f.read()

    appended_strings = []
    
    for key in keys_to_sync:
        # Check if already in target
        if f'name="{key}"' in target_content:
            continue
            
        # Find in source
        match = re.search(f'<string name="{key}">.*?</string>', source_content)
        if match:
            appended_strings.append("    " + match.group(0))
            
    if appended_strings:
        # insert before </resources>
        replacement = "\n" + "\n".join(appended_strings) + "\n</resources>"
        new_content = target_content.replace("</resources>", replacement)
        with open(target_file, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"Added {len(appended_strings)} strings to {lang}")
    else:
        print(f"No new strings for {lang}")
