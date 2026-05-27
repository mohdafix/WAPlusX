package com.wmods.wppenhacer.models;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import lombok.Generated;
import org.json.JSONArray;
import org.json.JSONException;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessage {
    public long a;
    public List<String> b;
    public List<String> c;
    public String d;
    public long e;
    public int f;
    public int g;
    public boolean h;
    public boolean i;
    public long j;
    public boolean k;
    public long l;
    public long m;
    public int n;
    public String o;

    public ScheduledMessage() {
        this.m = System.currentTimeMillis();
        this.h = true;
        this.i = false;
        this.j = 0L;
        this.k = false;
        this.l = 0L;
        this.g = 0;
        this.n = 0;
        this.b = new ArrayList();
        this.c = new ArrayList();
    }

    public static List<String> y(String str) {
        ArrayList arrayList = new ArrayList();
        if (str != null && !str.isEmpty() && !str.equals("[]")) {
            try {
                JSONArray jSONArray = new JSONArray(str);
                for (int i = 0; i < jSONArray.length(); i++) {
                    arrayList.add(jSONArray.getString(i));
                }
            } catch (JSONException unused) {
                arrayList.add(str);
            }
        }
        return arrayList;
    }

    public static String z(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        JSONArray jSONArray = new JSONArray();
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            jSONArray.put(it.next());
        }
        return jSONArray.toString();
    }

    public void A(boolean z) {
        this.h = z;
    }

    public void B(long j) {
        this.a = j;
    }

    public void C(String str) {
        this.o = str;
    }

    public void D(String str) {
        this.d = str;
    }

    public void E(int i) {
        this.g = i;
    }

    public void F(int i) {
        this.f = i;
    }

    public void G(long j) {
        this.e = j;
    }

    public void H(boolean z) {
        this.i = z;
    }

    public void I(int i) {
        this.n = i;
    }

    public boolean J() {
        if (!this.h) {
            return false;
        }
        int i = this.f;
        if ((i == 0 && this.i) || this.k) {
            return false;
        }
        return !(i == 4 && this.g != 0 && (i(Calendar.getInstance().get(7)) & this.g) == 0) && System.currentTimeMillis() >= p();
    }

    public void a(String str, String str2) {
        if (str != null) {
            this.b.add(str);
            List<String> list = this.c;
            if (str2 == null) {
                str2 = str.split("@")[0];
            }
            list.add(str2);
        }
    }

    public void b() {
        this.b.clear();
        this.c.clear();
    }

    public int c() {
        return this.b.size();
    }

    @Generated
    public List<String> d() {
        return this.b;
    }

    @Deprecated
    public String e() {
        return this.c.isEmpty() ? "" : this.c.get(0);
    }

    @Generated
    public List<String> f() {
        return this.c;
    }

    public String g() {
        if (this.c.isEmpty()) {
            return "";
        }
        if (this.c.size() == 1) {
            return this.c.get(0);
        }
        if (this.c.size() <= 3) {
            return String.join(", ", this.c);
        }
        return this.c.get(0) + " +" + (this.c.size() - 1);
    }

    @Generated
    public long h() {
        return this.m;
    }

    public final int i(int i) {
        switch (i) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 4;
            case 4:
                return 8;
            case 5:
                return 16;
            case 6:
                return 32;
            case 7:
                return 64;
            default:
                return 0;
        }
    }

    @Generated
    public long j() {
        return this.l;
    }

    @Generated
    public long k() {
        return this.a;
    }

    @Generated
    public String l() {
        return this.o;
    }

    @Generated
    public long m() {
        return this.j;
    }

    @Generated
    public String n() {
        return this.d;
    }

    public final long o(long j) {
        if (this.g != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(j);
            Calendar calendar2 = Calendar.getInstance();
            calendar2.setTimeInMillis(this.e);
            int i = calendar2.get(11);
            int i2 = calendar2.get(12);
            for (int i3 = 0; i3 < 7; i3++) {
                if ((i(calendar.get(7)) & this.g) != 0) {
                    calendar.set(11, i);
                    calendar.set(12, i2);
                    calendar.set(13, 0);
                    calendar.set(14, 0);
                    return calendar.getTimeInMillis();
                }
                calendar.add(5, 1);
            }
        }
        return j;
    }

    public long p() {
        if (this.f == 0) {
            return this.e;
        }
        System.currentTimeMillis();
        long j = this.j;
        if (j == 0) {
            return this.f == 4 ? o(this.e) : this.e;
        }
        int i = this.f;
        return i != 1 ? i != 2 ? i != 3 ? i != 4 ? j : o(j + 86400000) : j + 2592000000L : j + 604800000 : j + 86400000;
    }

    @Generated
    public int q() {
        return this.g;
    }

    @Generated
    public int r() {
        return this.f;
    }

    @Generated
    public long s() {
        return this.e;
    }

    @Generated
    public int t() {
        return this.n;
    }

    public boolean u() {
        String str = this.o;
        return (str == null || str.isEmpty()) ? false : true;
    }

    @Generated
    public boolean v() {
        return this.h;
    }

    @Generated
    public boolean w() {
        return this.k;
    }

    @Generated
    public boolean x() {
        return this.i;
    }

    public ScheduledMessage(long j, List<String> list, List<String> list2, String str, long j2, int i, int i2, boolean z, boolean z2, long j3, boolean z3, long j4, long j5, int i3, String str2) {
        this.a = j;
        this.b = list == null ? new ArrayList<>() : list;
        this.c = list2 == null ? new ArrayList<>() : list2;
        this.d = str;
        this.e = j2;
        this.f = i;
        this.g = i2;
        this.h = z;
        this.i = z2;
        this.j = j3;
        this.k = z3;
        this.l = j4;
        this.m = j5;
        this.n = i3;
        this.o = str2;
    }
}
