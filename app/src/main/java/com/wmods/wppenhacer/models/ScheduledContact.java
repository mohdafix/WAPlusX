package com.wmods.wppenhacer.models;

import java.io.Serializable;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledContact implements Serializable {
    public final String q;
    public final String s;

    public ScheduledContact(String str, String str2) {
        this.q = str;
        this.s = str2;
    }

    public String a() {
        String str = this.q;
        if (str != null && !str.isEmpty()) {
            return this.q;
        }
        String str2 = this.s;
        return str2 != null ? str2.split("@")[0] : "";
    }

    public String b() {
        return this.s;
    }

    public String c() {
        return this.q;
    }
}
