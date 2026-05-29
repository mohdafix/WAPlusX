package X;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public final class ne0 {
    public static final ne0 a = new ne0();

    public static final long a(int i, String[] strArr, long j, Class<?> cls) {
        long jA = ja2.a(j);
        int i2 = i / 8191;
        if (i2 < 0 || i2 >= strArr.length) {
            throw new IllegalArgumentException("Chunk index out of bounds: " + i2);
        }
        String str = strArr[i2];
        if (str == null) {
            if (cls == null) {
                throw new IllegalStateException("Chunk is null at index: " + i2);
            }
            try {
                Object objInvoke = cls.getMethod("ensureChunkLoaded", Integer.TYPE).invoke(null, Integer.valueOf(i2));
                str = (String) objInvoke;
                strArr[i2] = str;
                strArr[i2] = str;
            } catch (Exception e) {
                throw new RuntimeException("Failed to load chunk " + i2, e);
            }
        }
        int i3 = i - (i2 * 8191);
        if (i3 >= 0 && i3 < str.length()) {
            return (((long) str.charAt(i3)) << 32) ^ jA;
        }
        throw new IllegalArgumentException("Index in chunk out of bounds: " + i3 + ", chunk length: " + str.length());
    }

    public static final String b(long j, String[] strArr) {
        long jA = ja2.a(ja2.c(4294967295L & j));
        long j2 = (jA >>> 32) & 65535;
        long jA2 = ja2.a(jA);
        int i = (int) (((j >>> 32) ^ j2) ^ ((jA2 >>> 16) & 4294901760L));
        long jA3 = a(i, strArr, jA2, null);
        int i2 = (int) ((jA3 >>> 32) & 65535);
        char[] cArr = new char[i2];
        for (int i3 = 0; i3 < i2; i3++) {
            jA3 = a(i + i3 + 1, strArr, jA3, null);
            cArr[i3] = (char) ((jA3 >>> 32) & 65535);
        }
        return new String(cArr);
    }

    public static final String c(long j, String[] strArr, Class<?> cls) {
        long jA = ja2.a(ja2.c(4294967295L & j));
        long j2 = (jA >>> 32) & 65535;
        long jA2 = ja2.a(jA);
        int i = (int) (((j >>> 32) ^ j2) ^ ((jA2 >>> 16) & 4294901760L));
        long jA3 = a(i, strArr, jA2, cls);
        int i2 = (int) ((jA3 >>> 32) & 65535);
        char[] cArr = new char[i2];
        for (int i3 = 0; i3 < i2; i3++) {
            jA3 = a(i + i3 + 1, strArr, jA3, cls);
            cArr[i3] = (char) ((jA3 >>> 32) & 65535);
        }
        return new String(cArr);
    }

    public static final String[] d(byte[] bArr, long j) {
        try {
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bArr));
            String[] strArrE = a.e(dataInputStream, j);
            dataInputStream.close();
            return strArrE;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load obfuscated strings", e);
        }
    }

    public final String[] e(DataInputStream dataInputStream, long j) throws Exception {
        long j2 = 8191;
        int i = (int) (((j + j2) - 1) / j2);
        String[] strArr = new String[i];
        for (int i2 = 0; i2 < i; i2++) {
            strArr[i2] = "";
        }
        long j3 = 0;
        int i3 = 0;
        while (j3 < j) {
            int iMin = (int) Math.min(8191L, j - j3);
            char[] cArr = new char[iMin];
            for (int i4 = 0; i4 < iMin; i4++) {
                cArr[i4] = dataInputStream.readChar();
            }
            strArr[i3] = new String(cArr);
            j3 += (long) iMin;
            i3++;
        }
        return strArr;
    }
}
