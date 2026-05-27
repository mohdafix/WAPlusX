package com.wmods.wppenhacer.models;

import java.io.Serializable;
import java.util.Objects;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public final class Contact implements Serializable {
    public final String q;
    public final String s;

    public Contact(String str, String str2) {
        this.q = str;
        this.s = str2;
    }

    private /* synthetic */ boolean a(Object obj) {
        if (!(obj instanceof Contact)) {
            return false;
        }
        Contact x00Var = (Contact) obj;
        return Objects.equals(this.q, x00Var.q) && Objects.equals(this.s, x00Var.s);
    }



    public ScheduledContact d() {
        return new ScheduledContact(this.s, this.q);
    }

    public final boolean equals(Object obj) {
        return a(obj);
    }

    public final int hashCode() {
        return Objects.hash(this.q, this.s);
    }

    public final String toString() {
        return "Contact{" +
                "q='" + q + '\'' +
                ", s='" + s + '\'' +
                '}';
    }
}
