package X;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public final class ja2 {
    public static final ja2 a = new ja2();

    public static final long a(long j) {
        short s = (short) (j & 65535);
        short s2 = (short) ((j >>> 16) & 65535);
        short sB = (short) (b((short) (s + s2), 9) + s);
        short s3 = (short) (s2 ^ s);
        return (((((long) b(s3, 10)) & 65535) | ((((long) sB) & 65535) << 16)) << 16) | (65535 & ((long) ((short) (((short) (b(s, 13) ^ s3)) ^ (s3 << 5)))));
    }

    public static final short b(short s, int i) {
        int i2 = s & 65535;
        return (short) ((i2 >>> (16 - i)) | (i2 << i));
    }

    public static final long c(long j) {
        long j2 = (j ^ (j >>> 33)) * 7109453100751455733L;
        return ((j2 ^ (j2 >>> 28)) * (-3808689974395783757L)) >>> 32;
    }
}
