// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.miracast.core.WfdQuality;

/**
 * Screen mirroring settings: on/off, the name phones see, ask before connecting, picture quality,
 * and how to connect. No launcher tile: reached from Settings > Apps > Screen mirroring > Open
 * (MAIN + INFO), the service notification, or the action org.lumen.miracast.SETTINGS (the projector's
 * quick menu tile, Lumen Home). With the extra {@link #EXTRA_EDIT} = "name" (the quick menu's Name row)
 * it opens straight on the name field and closes again when that is done.
 */
public final class SettingsActivity extends Activity implements MiracastService.UiListener {
    static final String EXTRA_EDIT = "edit";
    private SharedPreferences prefs;
    /** Opened only to rename (EXTRA_EDIT = "name"): the screen closes with the name field. */
    private boolean nameOnly;
    private TextView statusValue, enabledValue, nameValue, askValue, qualityValue, howto;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        setContentView(build());
        nameOnly = b == null && "name".equals(getIntent().getStringExtra(EXTRA_EDIT));
        if (nameOnly) getWindow().getDecorView().post(this::editName);
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[] {Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS}, 1);
            } catch (Throwable ignored) {
                // no permission UI: granted in the image by default-permissions
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        MiracastService.sync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MiracastService.addListener(this);
        if (Prefs.enabled(this)) MiracastService.sync(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MiracastService.removeListener(this);
    }

    @Override
    public void onMiracastState() {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        boolean on = Prefs.enabled(this);
        String name = Prefs.name(this);
        enabledValue.setText(on ? R.string.on : R.string.off);
        nameValue.setText(name);
        askValue.setText(Prefs.ask(this) ? R.string.on : R.string.off);
        WfdQuality q = Prefs.quality(this);
        qualityValue.setText(q == WfdQuality.P1080_30 ? R.string.quality_1080_30
                : q == WfdQuality.P720_60 ? R.string.quality_720 : R.string.quality_1080_60);
        String st = StatusText.of(this, StatusText.status(this));
        statusValue.setText(st);
        howto.setText(getString(R.string.howto_android, name) + "\n" + getString(R.string.howto_windows, name));
    }

    private void changed(SharedPreferences.Editor e) {
        e.commit();
        MiracastService.sync(this);
        refresh();
    }

    // ------------------------------------------------------------------ views

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setDefaultFocusHighlightEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(48);
        list.setPadding(pad, dp(32), pad, dp(32));
        sv.addView(list);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(16));
        list.addView(title);

        statusValue = info(list, R.string.status_title);
        View enabled = row(list, R.string.row_enabled);
        enabledValue = value(enabled);
        enabled.setOnClickListener(v -> changed(prefs.edit().putBoolean(Prefs.ENABLED, !Prefs.enabled(this))));
        // hidden status page for support (stage report): long press OK on the first row
        enabled.setOnLongClickListener(v -> {
            startActivity(new android.content.Intent(this, DiagActivity.class));
            return true;
        });
        View nameRow = row(list, R.string.row_name);
        nameValue = value(nameRow);
        nameRow.setOnClickListener(v -> editName());
        View ask = row(list, R.string.row_ask);
        askValue = value(ask);
        ask.setOnClickListener(v -> changed(prefs.edit().putBoolean(Prefs.ASK, !Prefs.ask(this))));
        View quality = row(list, R.string.row_quality);
        qualityValue = value(quality);
        quality.setOnClickListener(v -> {
            WfdQuality q = Prefs.quality(this);
            WfdQuality next = q == WfdQuality.P1080_60 ? WfdQuality.P1080_30
                    : q == WfdQuality.P1080_30 ? WfdQuality.P720_60 : WfdQuality.P1080_60;
            changed(prefs.edit().putString(Prefs.QUALITY, next.name()));
        });
        howto = info(list, R.string.howto_title);
        enabled.requestFocus();
        return sv;
    }

    private View row(LinearLayout list, int label) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackgroundResource(R.drawable.row_bg);
        r.setPadding(dp(20), dp(14), dp(20), dp(14));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        l.setTextColor(getColor(R.color.text_primary));
        r.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView v = new TextView(this);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setTextColor(getColor(R.color.text_secondary));
        v.setTag("value");
        r.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        list.addView(r, lp);
        return r;
    }

    private static TextView value(View row) {
        return row.findViewWithTag("value");
    }

    private TextView info(LinearLayout list, int label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        l.setTextColor(getColor(R.color.text_secondary));
        l.setPadding(dp(4), dp(12), 0, dp(4));
        list.addView(l);
        TextView v = new TextView(this);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        v.setTextColor(getColor(R.color.text_primary));
        v.setPadding(dp(4), 0, 0, dp(12));
        list.addView(v);
        return v;
    }

    private void editName() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setFilters(new InputFilter[] {new InputFilter.LengthFilter(32)});
        e.setText(Prefs.name(this));
        e.setSelection(e.getText().length());
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.name_dialog_title)
                .setView(e)
                .setPositiveButton(R.string.ok, (dd, w) -> {
                    String n = Prefs.clip32(e.getText().toString().trim());
                    // the device name itself (or nothing): no own name, so a later rename of the device follows
                    boolean own = !n.isEmpty() && !n.equals(Prefs.systemName(this));
                    changed(prefs.edit().putString(Prefs.NAME, own ? n : null));
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        if (nameOnly) d.setOnDismissListener(x -> finish());
        d.show();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
