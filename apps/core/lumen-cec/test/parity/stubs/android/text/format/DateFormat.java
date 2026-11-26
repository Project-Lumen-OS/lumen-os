// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.text.format;

import android.content.Context;

public final class DateFormat {
    private DateFormat() { }
    public static java.text.DateFormat getTimeFormat(Context c) { return new java.text.SimpleDateFormat("HH:mm"); }
}
