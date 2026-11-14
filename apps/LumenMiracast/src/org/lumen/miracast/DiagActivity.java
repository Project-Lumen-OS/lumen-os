// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Hidden status page for support: the stage report of the receiver (where the last attempt stopped)
 * and of the last finished attempt. Technical text, not translated. Opened by a long press on the
 * first row of the screen mirroring settings, or from adb:
 * {@code adb shell am start -n org.lumen.miracast/.DiagActivity}. Refreshes on every state change;
 * OK refreshes by hand, BACK closes.
 */
public final class DiagActivity extends Activity implements MiracastService.UiListener {
    private TextView text;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF101214);
        sv.setFocusable(true);
        text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int p = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
        text.setPadding(p, p, p, p);
        sv.addView(text);
        setContentView(sv);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MiracastService.addListener(this);
        onMiracastState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MiracastService.removeListener(this);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            onMiracastState();
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override
    public void onMiracastState() {
        runOnUiThread(() -> {
            MiracastService s = MiracastService.instance();
            StringBuilder sb = new StringBuilder();
            sb.append("Screen mirroring diagnostics\n");
            sb.append("receiver: ").append(s != null ? "running" : "not running")
                    .append(", enabled=").append(Prefs.enabled(this)).append(", ask=").append(Prefs.ask(this))
                    .append(", name=").append(Prefs.name(this)).append("\n\n");
            sb.append(MiracastService.diag().report());
            sb.append("\nlast finished attempt:\n").append(MiracastService.lastReport(this));
            text.setText(sb);
        });
    }
}
