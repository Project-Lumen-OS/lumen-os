/*
 * The text dialogs of the casting screen: the fixed code (a text field) and the licence texts. The choice rows (code
 * mode, quality, delay) are rows of the casting screen itself (old-ui-1125), the old AirPlay settings screen is gone.
 *
 * Copyright (C) 2026 Z9X project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.lumen.airplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

final class AirPlayDialogs {
    private final Activity a;
    private final SharedPreferences prefs;
    /** Saves an edit and lets the receiver pick it up (the screen's own refresh follows). */
    private final Consumer<SharedPreferences.Editor> changed;

    AirPlayDialogs(Activity a, Consumer<SharedPreferences.Editor> changed) {
        this.a = a;
        this.prefs = Prefs.get(a);
        this.changed = changed;
    }

    void editFixedPin(boolean selectMode) {
        EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
        et.setHint(R.string.fixed_pin_hint);
        String cur = prefs.getString(Prefs.FIXED_PIN, "");
        if (Prefs.validPin(cur)) et.setText(cur);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(R.string.access_fixed)
                .setView(pad(et))
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p = et.getText().toString().trim();
            if (!Prefs.validPin(p)) {
                et.setError(a.getString(R.string.fixed_pin_invalid));
                return;
            }
            SharedPreferences.Editor e = prefs.edit().putString(Prefs.FIXED_PIN, p);
            if (selectMode) e.putInt(Prefs.PIN_MODE, NativeBridge.PIN_FIXED);
            changed.accept(e);
            d.dismiss();
        }));
        d.show();
    }

    void showLicences() {
        String[] files;
        try {
            files = a.getAssets().list("licenses");
        } catch (Exception e) {
            files = null;
        }
        if (files == null || files.length == 0) return;
        Arrays.sort(files);
        final String[] names = files;
        new AlertDialog.Builder(a)
                .setTitle(R.string.set_licenses)
                .setItems(names, (d, which) -> showText(names[which], readAsset("licenses/" + names[which])))
                .show();
    }

    private void showText(String title, String body) {
        TextView tv = new TextView(a);
        tv.setText(body);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setFocusable(true);
        tv.setPadding(dp(24), dp(8), dp(24), dp(8));
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        new AlertDialog.Builder(a).setTitle(title).setView(sv)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private String readAsset(String path) {
        try (InputStream in = a.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return String.valueOf(e);
        }
    }

    private View pad(View v) {
        FrameLayout f = new FrameLayout(a);
        f.setPadding(dp(24), dp(8), dp(24), 0);
        f.addView(v);
        return f;
    }


    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }
}
