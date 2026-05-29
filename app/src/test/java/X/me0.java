package X;

/* JADX INFO: loaded from: classes.dex */
public class me0 {
    public static volatile String[] a;

    public static long a(int i, long j) {
        long jA = ja2.a(j);
        int i2 = i / 8191;
        String strC = a[i2];
        if (strC == null) {
            strC = c(i2);
            a[i2] = strC;
        }
        return jA ^ (((long) strC.charAt(i - (i2 * 8191))) << 32);
    }

    public static String b(long j) {
        if (a == null) {
            a = new String[12];
        }
        long jA = ja2.a(ja2.c(4294967295L & j));
        long j2 = (jA >>> 32) & 65535;
        long jA2 = ja2.a(jA);
        int i = (int) (((j >>> 32) ^ j2) ^ ((jA2 >>> 16) & 4294901760L));
        long jA3 = a(i, jA2);
        int i2 = (int) ((jA3 >>> 32) & 65535);
        char[] cArr = new char[i2];
        for (int i3 = 0; i3 < i2; i3++) {
            jA3 = a(i + i3 + 1, jA3);
            cArr[i3] = (char) ((jA3 >>> 32) & 65535);
        }
        return new String(cArr);
    }

    public static String c(int i) {
        switch (i) {
            case 0:
                return ne0.d(ae0.a, 8191L)[0];
            case 1:
                return ne0.d(de0.a, 8191L)[0];
            case 2:
                return ne0.d(ee0.a, 8191L)[0];
            case 3:
                return ne0.d(fe0.a, 8191L)[0];
            case 4:
                return ne0.d(ge0.a, 8191L)[0];
            case 5:
                return ne0.d(he0.a, 8191L)[0];
            case 6:
                return ne0.d(ie0.a, 8191L)[0];
            case 7:
                return ne0.d(je0.a, 8191L)[0];
            case 8:
                return ne0.d(ke0.a, 8191L)[0];
            case 9:
                return ne0.d(le0.a, 8191L)[0];
            case 10:
                return ne0.d(be0.a, 8191L)[0];
            case 11:
                return ne0.d(ce0.a, 3318L)[0];
            default:
                throw new IllegalArgumentException("Invalid chunk index");
        }
    }
}
