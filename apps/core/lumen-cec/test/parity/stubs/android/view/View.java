// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.view;

import android.content.Context;

public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    private int visibility = VISIBLE;
    public View(Context c) { }
    public void setVisibility(int v) { visibility = v; }
    public int getVisibility() { return visibility; }
    public void invalidate() { }
}
