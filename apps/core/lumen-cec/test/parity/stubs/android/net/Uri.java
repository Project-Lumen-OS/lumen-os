// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.net;

public final class Uri {
    private final String s;
    private Uri(String s) { this.s = s; }
    public static Uri parse(String s) { return new Uri(s); }
    public String getAuthority() {
        int a = s.indexOf("://");
        String rest = a < 0 ? s : s.substring(a + 3);
        int e = rest.indexOf('/');
        return e < 0 ? rest : rest.substring(0, e);
    }
    @Override public String toString() { return s; }
}
