// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.kbpreview;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Test only: a city field like Lumen Home's weather search, focused, with the keyboard up. */
public final class Field extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, 120, 0, 0);
        l.setBackgroundColor(0xFF16181C);
        EditText e = new EditText(this);
        e.setHint(getIntent().getStringExtra("hint") != null ? getIntent().getStringExtra("hint") : "City");
        e.setTextSize(26);
        e.setTextColor(0xFFF1F3F4);
        e.setHintTextColor(0x99F1F3F4);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        l.addView(e, new LinearLayout.LayoutParams(900, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(l);
        e.requestFocus();
        e.postDelayed(() -> getSystemService(InputMethodManager.class).showSoftInput(e, 0), 500);
    }
}
