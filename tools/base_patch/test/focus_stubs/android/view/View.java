// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.view;

/**
 * Host stand-in for test/FocusRowsTest.java: the parts of android.view.View a list row's focus and OK depend on.
 * canTakeFocus() is the framework's rule (focusable AND enabled); OK on a disabled view is eaten without a click
 * (View.onKeyUp).
 */
public class View {
    public interface OnClickListener {
        void onClick(View v);
    }

    private boolean enabled = true, focusable, clickable;
    private OnClickListener click;

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setFocusable(boolean focusable) { this.focusable = focusable; }
    public boolean isFocusable() { return focusable; }
    public void setClickable(boolean clickable) { this.clickable = clickable; }
    public boolean isClickable() { return clickable; }

    public void setOnClickListener(OnClickListener l) {
        click = l;
        if (l != null) clickable = true;
    }

    public boolean canTakeFocus() { return focusable && enabled; }

    /** DPAD_CENTER up on this view */
    public void pressOk() {
        if (!enabled || !clickable || click == null) return;
        click.onClick(this);
    }
}
