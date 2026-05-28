import os
import re
import struct
import base64

def uint64(n):
    return n & 0xFFFFFFFFFFFFFFFF

def rotl16(x, k):
    x = x & 0xFFFF
    return ((x << k) | (x >> (16 - k))) & 0xFFFF

def q72_a(j):
    j = uint64(j)
    s = j & 0xFFFF
    s2 = (j >> 16) & 0xFFFF
    sB = (rotl16((s + s2) & 0xFFFF, 9) + s) & 0xFFFF
    s3 = s2 ^ s
    part1 = rotl16(s3, 10) & 0xFFFF
    part2 = (sB & 0xFFFF) << 16
    part3 = (rotl16(s, 13) ^ s3 ^ ((s3 << 5) & 0xFFFF)) & 0xFFFF
    return uint64((part1 | part2) << 16) | part3

def q72_c(j):
    j = uint64(j)
    j2 = uint64((j ^ (j >> 33)) * uint64(7109453100751455733))
    return uint64((j2 ^ (j2 >> 28)) * uint64(-3808689974395783757)) >> 32

chunks = []

def cd0_a(i, j):
    global chunks
    j = uint64(j)
    jA = q72_a(j)
    i2 = i // 8191
    strC = chunks[i2]
    char_idx = i - (i2 * 8191)
    return jA ^ (ord(strC[char_idx]) << 32)

def cd0_b(j):
    global chunks
    j = uint64(j)
    jA = q72_a(q72_c(j & 0xFFFFFFFF))
    j2 = (jA >> 32) & 0xFFFF
    jA2 = q72_a(jA)
    p3 = (jA2 >> 16) & 0xFFFF0000
    i = ((j >> 32) ^ j2 ^ p3) & 0xFFFFFFFF
    if i >= 0x80000000:
        i -= 0x100000000
    
    jA3 = cd0_a(i, jA2)
    i2 = (jA3 >> 32) & 0xFFFF
    chars = []
    for k in range(i2):
        jA3 = cd0_a(i + k + 1, jA3)
        ch = (jA3 >> 32) & 0xFFFF
        chars.append(chr(ch) if ch < 0x10000 else '?')
    return "".join(chars)

def extractBase64(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    m = re.search(r'dk\.a\("([\s\S]*?)"\)', content)
    if not m:
        m = re.search(r'"([A-Za-z0-9+/=]{100,})"', content)
    return m.group(1).replace("\n", "").replace(" ", "").replace("\r", "")

def decodeChunkFile(path, lens):
    b64 = extractBase64(path)
    raw = base64.b64decode(b64)
    charCount = len(raw) // 2
    if 0 < lens < charCount:
        charCount = lens
    chars = []
    for i in range(charCount):
        char_val = struct.unpack('>H', raw[i*2:i*2+2])[0]
        chars.append(chr(char_val))
    return "".join(chars)

base = r"C:\Users\user\Downloads\wae-latest26\sources\X\\"
names = ["ae0", "de0", "ee0", "fe0", "ge0", "he0", "ie0", "je0", "ke0", "le0", "be0", "ce0"]
lens = [8191, 8191, 8191, 8191, 8191, 8191, 8191, 8191, 8191, 8191, 8191, 3368]

for i in range(len(names)):
    try:
        chunks.append(decodeChunkFile(base + names[i] + ".java", lens[i]))
    except Exception as e:
        chunks.append("")

def deobfuscate_file(in_path, out_path, target_pkg):
    with open(in_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    def repl(m):
        id_val = int(m.group(1))
        try:
            val = cd0_b(id_val)
            val = val.replace('\\', '\\\\').replace('"', '\\"')
            return f'"{val}"'
        except Exception as e:
            return m.group(0)
    
    content = re.sub(r'me0\.b\((-?\d+)L\)', repl, content)
    
    # Class renames
    content = content.replace("X.gi2", "com.wmods.wppenhacer.models.ScheduledMessage")
    content = content.replace("gi2", "ScheduledMessage")
    content = content.replace("X.li2", "com.wmods.wppenhacer.db.ScheduledMessageDatabase")
    content = content.replace("li2", "ScheduledMessageDatabase")
    content = content.replace("X.hi2", "com.wmods.wppenhacer.utils.ScheduledMessageHelper")
    content = content.replace("hi2", "ScheduledMessageHelper")
    content = content.replace("X.mi2", "com.wmods.wppenhacer.ui.adapters.ScheduledMessagesAdapter")
    content = content.replace("mi2", "ScheduledMessagesAdapter")
    content = content.replace("X.q00", "com.wmods.wppenhacer.models.ScheduledContact")
    content = content.replace("q00", "ScheduledContact")
    content = content.replace("X.x00", "com.wmods.wppenhacer.models.Contact")
    
    # AndroidX and UI obfuscated classes replacements
    content = content.replace("import X.m7;", "import androidx.activity.result.ActivityResultLauncher;")
    content = content.replace("public m7<String>", "public ActivityResultLauncher<String>")
    
    content = content.replace("import X.y6;", "import androidx.activity.result.ActivityResultCallback;")
    content = content.replace("implements y6", "implements ActivityResultCallback")
    content = content.replace("y6 ", "ActivityResultCallback ")
    
    content = content.replace("import X.b7;", "import androidx.activity.result.contract.ActivityResultContracts;")
    content = content.replace("new b7()", "new ActivityResultContracts.GetContent()")
    
    content = content.replace("import X.k7;", "import androidx.activity.result.contract.ActivityResultContracts;")
    content = content.replace("new k7()", "new ActivityResultContracts.RequestPermission()")
    
    content = content.replace("import X.dx1;", "import androidx.activity.OnBackPressedCallback;")
    content = content.replace("extends dx1", "extends OnBackPressedCallback")
    
    content = content.replace("import androidx.fragment.app.e;", "import androidx.fragment.app.Fragment;")
    content = content.replace("extends e ", "extends Fragment ")
    
    content = content.replace("import androidx.fragment.app.f;", "import androidx.fragment.app.FragmentActivity;")
    content = content.replace("f activity", "FragmentActivity activity")
    
    content = content.replace("import X.qw0;", "import com.wmods.wppenhacer.databinding.FragmentScheduleEditorBinding;")
    content = content.replace("public qw0 q;", "public FragmentScheduleEditorBinding q;")
    content = content.replace("qw0 qw0VarC = qw0.c(layoutInflater, viewGroup, false);", "FragmentScheduleEditorBinding qw0VarC = FragmentScheduleEditorBinding.inflate(layoutInflater, viewGroup, false);")
    content = content.replace("qw0 qw0Var =", "FragmentScheduleEditorBinding qw0Var =")
    content = content.replace("return qw0VarC.b();", "return qw0VarC.getRoot();")
    
    content = content.replace("import X.rw0;", "import com.wmods.wppenhacer.databinding.FragmentScheduledMessagesBinding;")
    content = content.replace("public rw0 B;", "public FragmentScheduledMessagesBinding B;")
    content = content.replace("rw0 rw0VarC = rw0.c(layoutInflater, viewGroup, false);", "FragmentScheduledMessagesBinding rw0VarC = FragmentScheduledMessagesBinding.inflate(layoutInflater, viewGroup, false);")
    content = content.replace("return rw0VarC.b();", "return rw0VarC.getRoot();")
    
    content = content.replace("import X.tc;", "import androidx.appcompat.app.AppCompatActivity;")
    content = content.replace("(tc)", "(AppCompatActivity)")
    content = content.replace("tc tcVar =", "AppCompatActivity tcVar =")
    
    content = content.replace("import X.z5;", "import androidx.appcompat.app.ActionBar;")
    content = content.replace("z5 supportActionBar;", "ActionBar supportActionBar;")
    
    content = content.replace("import X.si1;", "import com.google.android.material.dialog.MaterialAlertDialogBuilder;")
    content = content.replace("new si1", "new MaterialAlertDialogBuilder")
    
    content = content.replace("import androidx.appcompat.app.a;", "import androidx.appcompat.app.AlertDialog;\nimport com.google.android.material.dialog.MaterialAlertDialogBuilder;")
    content = content.replace("a.C0066a", "MaterialAlertDialogBuilder")
    
    content = content.replace("import X.fv3;", "import com.wmods.wppenhacer.xposed.utils.Utils;")
    content = content.replace("fv3.", "Utils.")
    
    content = content.replace("import X.sp3;", "import com.wmods.wppenhacer.xposed.utils.Utils;")
    content = content.replace("sp3.a", "Utils.isNullOrEmpty")
    
    content = content.replace("import X.ij1;", "import androidx.core.content.ContextCompat;")
    content = content.replace("ij1.d", "ContextCompat.getColor")
    content = content.replace("import X.f72;", "import com.wmods.wppenhacer.R;")
    content = content.replace("f72.k", "R.color.md_theme_light_primary")
    content = content.replace("f72.o", "R.color.md_theme_light_primaryContainer")
    content = content.replace("f72.n", "R.color.md_theme_light_tertiary")
    content = content.replace("f72.t", "R.color.md_theme_light_tertiaryContainer")
    content = content.replace("f72.m", "R.color.md_theme_light_error")
    content = content.replace("f72.s", "R.color.md_theme_light_errorContainer")
    
    content = content.replace("import X.me0;", "")
    content = content.replace("import q10;", "")
    content = content.replace("import qv1;", "")
    content = content.replace("import im2;", "")
    
    # Package replacements
    content = re.sub(r'^package X;', f'package {target_pkg};', content, flags=re.MULTILINE)
    
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(content)

mappings = [
    (r"C:\Users\user\Downloads\wae-latest26\sources\com\wmods\wppenhacer\ui\fragments\ScheduledMessagesFragment.java", r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\ui\fragments\ScheduledMessagesFragment.java", "com.wmods.wppenhacer.ui.fragments"),
    (r"C:\Users\user\Downloads\wae-latest26\sources\com\wmods\wppenhacer\ui\fragments\ScheduleEditorFragment.java", r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\ui\fragments\ScheduleEditorFragment.java", "com.wmods.wppenhacer.ui.fragments"),
    (r"C:\Users\user\Downloads\wae-latest26\sources\X\mi2.java", r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\ui\adapters\ScheduledMessagesAdapter.java", "com.wmods.wppenhacer.ui.adapters"),
    (r"C:\Users\user\Downloads\wae-latest26\sources\X\q00.java", r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\models\ScheduledContact.java", "com.wmods.wppenhacer.models"),
    (r"C:\Users\user\Downloads\wae-latest26\sources\X\x00.java", r"C:\Project\WAE\app\src\main\java\com\wmods\wppenhacer\models\Contact.java", "com.wmods.wppenhacer.models")
]

for in_p, out_p, pkg in mappings:
    deobfuscate_file(in_p, out_p, pkg)
    print(f"Ported {os.path.basename(in_p)}")
