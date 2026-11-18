// SPDX-License-Identifier: Apache-2.0
package org.lumen.about;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.qr.QrView;

import java.util.Locale;

/**
 * Settings > About > Back to the XGIMI system, the same screen on every projector: the model code, the XGIMI firmware,
 * the exact file name the USB stick needs, the official XGIMI file when Lumen Installer's catalog knows it for this
 * model and firmware, and a QR code of that file (else of the guide's Back to stock section). It only shows facts:
 * nothing on the projector changes. Opened from TvSettings, so it is exported.
 */
public final class XgimiSystemActivity extends Activity {
    private static final int BG = 0xFF15171B, PILL = 0xFF2A2E35, TEXT = 0xFFF1EEE8, DIM = 0xFFB5B1AA, WARN = 0xFFE9CF9C;
    private static final int QR_DP = 220;

    private XgimiFacts facts;
    private RestoreCatalog catalog;
    private boolean searching = true;
    private LinearLayout rows;
    private QrView qr;
    private TextView link;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        facts = UnitFacts.read();
        build();
        render();
        new Thread(() -> {
            RestoreCatalog kept = CatalogSource.kept(getApplicationContext());
            runOnUiThread(() -> show(kept, true));
            RestoreCatalog fresh = CatalogSource.fresh(getApplicationContext(), kept);
            runOnUiThread(() -> show(fresh != null ? fresh : kept, false));
        }, "lumen-about-catalog").start();
    }

    private void show(RestoreCatalog c, boolean stillSearching) {
        if (isFinishing() || isDestroyed()) return;
        catalog = c;
        searching = stillSearching;
        render();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(48), dp(28), dp(48), dp(24));
        root.addView(text(getString(R.string.lumen_about_back), 28, TEXT, true));
        TextView intro = text(getString(R.string.lumen_about_intro), 18, DIM, false);
        intro.setPadding(0, dp(8), 0, dp(12));
        root.addView(intro);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(cols, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        cols.addView(rows, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        side.setPadding(dp(24), 0, 0, 0);
        qr = new QrView(this, Guide.backToStock(language()));
        side.addView(qr, new LinearLayout.LayoutParams(dp(QR_DP), dp(QR_DP)));
        TextView scan = text(getString(R.string.lumen_about_scan), 16, DIM, false);
        scan.setGravity(Gravity.CENTER);
        scan.setPadding(0, dp(10), 0, dp(4));
        side.addView(scan, new LinearLayout.LayoutParams(dp(QR_DP + 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        link = text("", 12, DIM, false);
        link.setGravity(Gravity.CENTER);
        side.addView(link, new LinearLayout.LayoutParams(dp(QR_DP + 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        cols.addView(side, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = text(getString(R.string.lumen_about_close), 18, TEXT, false);
        close.setFocusable(true);
        close.setBackground(pill());
        close.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {BG, TEXT}));
        close.setPadding(dp(28), dp(10), dp(28), dp(10));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        root.addView(close, clp);
        setContentView(root);
        close.requestFocus();
    }

    private void render() {
        WayBack w = WayBack.of(facts, catalog, language());
        rows.removeAllViews();
        row(R.string.lumen_about_model_code, w.model);
        row(R.string.lumen_about_firmware, w.firmware);
        row(R.string.lumen_about_usb_file, w.usbFile.isEmpty() ? getString(R.string.lumen_about_guide_files) : w.usbFile);
        String official = w.official != null
                ? getString(R.string.lumen_about_official_value, XgimiFacts.shown(w.official.version),
                        String.format(Locale.getDefault(), "%,d", w.official.size))
                : getString(searching ? R.string.lumen_about_searching : R.string.lumen_about_guide_files);
        row(R.string.lumen_about_official, official);
        TextView erase = text(getString(R.string.lumen_about_erases), 18, WARN, false);
        erase.setPadding(0, dp(14), 0, 0);
        rows.addView(erase);
        qr.setText(w.link);
        link.setText(w.link);
    }

    private void row(int label, String value) {
        if (value.isEmpty()) return;
        TextView l = text(getString(label), 16, DIM, false);
        l.setPadding(0, dp(10), 0, 0);
        rows.addView(l);
        rows.addView(text(value, 22, TEXT, false));
    }

    private String language() {
        return getResources().getConfiguration().getLocales().get(0).getLanguage();
    }

    /** the button look of the Lumen screens: a dark pill at rest, a light one with dark text in focus */
    private StateListDrawable pill() {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[] {android.R.attr.state_focused}, round(TEXT));
        sl.addState(new int[0], round(PILL));
        return sl;
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(24));
        return g;
    }
}
