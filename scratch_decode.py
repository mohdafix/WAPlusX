import sys
import base64
import re
import struct
import os

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
    if i2 < 0 or i2 >= len(chunks):
        raise Exception(f"invalid chunk {i2} for idx {i}")
    strC = chunks[i2]
    if not strC:
        raise Exception(f"missing chunk {i2}")
    char_idx = i - (i2 * 8191)
    if char_idx >= len(strC):
        raise Exception(f"char index {char_idx} out of range for chunk {i2} (len={len(strC)})")
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
    if i2 > 2000 or i2 < 0:
        raise Exception(f"Invalid string length: {i2}")
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
        if not m:
            raise Exception("no b64 in " + path)
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

import glob

all_files = glob.glob(r"C:\Users\user\Downloads\wae-latest26\sources\**\*.java", recursive=True)

for fpath in all_files:
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()
            ids = re.findall(r'me0\.b\((-?\d+)L\)', content)
            for i in set(ids):
                id_val = int(i)
                try:
                    result = cd0_b(id_val)
                    if "hide" in result.lower() or "receipt" in result.lower():
                        print(f"FOUND {result} in {fpath} with ID {id_val}")
                except Exception as e:
                    pass
    except Exception as e:
        pass
