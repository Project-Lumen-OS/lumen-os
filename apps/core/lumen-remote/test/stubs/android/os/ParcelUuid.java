// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;
public final class ParcelUuid {
    private final String u;
    private ParcelUuid(String u) { this.u = u.toLowerCase(java.util.Locale.ROOT); }
    public static ParcelUuid fromString(String s) { return new ParcelUuid(s); }
    @Override public boolean equals(Object o) { return o instanceof ParcelUuid && ((ParcelUuid) o).u.equals(u); }
    @Override public int hashCode() { return u.hashCode(); }
    @Override public String toString() { return u; }
}
