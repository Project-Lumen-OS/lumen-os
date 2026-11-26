// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.text;

public final class TextUtils {
    private TextUtils() { }
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
}
