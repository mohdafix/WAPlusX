import re

with open(r'C:\Users\user\Downloads\wae-latest26\resources\res\values\strings.xml', 'r', encoding='utf-8') as f:
    data = f.read()

matches = re.findall(r'<string name="schedule_.*?<\/string>', data)
with open(r'C:\Project\WAE\schedule_strings.xml', 'w', encoding='utf-8') as f:
    f.write('\n'.join(matches))
