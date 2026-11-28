// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.view;

import java.util.ArrayList;
import java.util.List;

/** Host stand-in for test/FocusRowsTest.java: models only what the focus of a settings row depends on. */
public class ViewGroup extends View {
    private final List<View> children = new ArrayList<>();

    public void addView(View child) { children.add(child); }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int i) { return children.get(i); }
}
