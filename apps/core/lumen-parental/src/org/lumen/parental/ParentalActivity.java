// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * The parent page (stock XGIMI Home ParentSettingActivity / 家长设置), opened from the projector menu's General row
 * (intent org.lumen.parental.SETTINGS) and from Home. With the lock on it opens only after the password (stock: without
 * the lock no password). Rows in the stock order: child lock, control range, time per session, time per day, power on
 * password, child mode at power on; then child mode, its apps, the password change and the eye protection hint.
 */
public final class ParentalActivity extends Activity {
    private static final int ASK_OPEN = 1, ASK_SET = 2, ASK_CHANGE = 3;
    private ParentalState s;
    private LinearLayout list;
    private boolean apps;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = Parental.load(this);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Look.BG);
        int m = Look.dp(this, 48);
        root.setPadding(m, Look.dp(this, 40), m, Look.dp(this, 24));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Look.text(this, getString(R.string.lumen_lp_title), 28, Look.TEXT, true));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = Look.dp(this, 12);
        left.addView(Look.text(this, getString(R.string.lumen_lp_intro), 15, Look.SUB, false), ip);
        root.addView(left, new LinearLayout.LayoutParams(0, -1, 4));
        ScrollView sv = new ScrollView(this);
        sv.setDefaultFocusHighlightEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        FrameLayout clip = new FrameLayout(this);
        clip.addView(sv);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 6);
        rp.leftMargin = Look.dp(this, 36);
        root.addView(clip, rp);
        setContentView(root);
        if (s.lockOn) startActivityForResult(PinActivity.forResult(this, PinActivity.VERIFY), ASK_OPEN);
        rows();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == ASK_OPEN && res != RESULT_OK) {
            finish();
            return;
        }
        s = Parental.load(this);
        if (req == ASK_SET && res == RESULT_OK) {
            s.lockOn = true;
            save();
        }
        rows();
    }

    private void save() { Parental.save(this, s); }

    private Look.Row row(int title) {
        Look.Row r = new Look.Row(this, getString(title));
        list.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private void rows() {
        apps = false;
        list.removeAllViews();
        Look.Row lock = row(R.string.lumen_lp_lock).sub(getString(R.string.lumen_lp_lock_desc)).check(s.lockOn);
        lock.setOnClickListener(v -> {
            if (!s.lockOn) startActivityForResult(PinActivity.forResult(this, PinActivity.SET), ASK_SET);
            else confirmOff();
        });
        if (s.lockOn) {
            Look.Row range = row(R.string.lumen_lp_range).value(getString(s.range == ControlRange.GLOBAL ? R.string.lumen_lp_range_global
                    : R.string.lumen_lp_range_child));
            range.setOnClickListener(v -> {
                s.range = s.range == ControlRange.GLOBAL ? ControlRange.CHILD_ONLY : ControlRange.GLOBAL;
                save();
                refresh(v);
            });
            Look.Row session = row(R.string.lumen_lp_session).sub(getString(R.string.lumen_lp_session_desc)).value(minutes(s.sessionMin));
            session.setOnClickListener(v -> {
                s.sessionMin = next(ParentalState.SESSION_MIN, s.sessionMin);
                save();
                refresh(v);
            });
            Look.Row daily = row(R.string.lumen_lp_daily).value(minutes(s.dailyMin));
            daily.setOnClickListener(v -> {
                s.dailyMin = next(ParentalState.DAILY_MIN, s.dailyMin);
                save();
                refresh(v);
            });
            Look.Row bootPin = row(R.string.lumen_lp_boot_pin).check(s.bootPin);
            bootPin.setOnClickListener(v -> {
                s.bootPin = !s.bootPin;
                save();
                refresh(v);
            });
            Look.Row bootChild = row(R.string.lumen_lp_boot_child).check(s.bootChild);
            bootChild.setOnClickListener(v -> {
                s.bootChild = !s.bootChild;
                save();
                refresh(v);
            });
            Look.Row child = row(R.string.lumen_lp_child_mode).check(s.childMode);
            child.setOnClickListener(v -> {
                s.childMode = !s.childMode;
                save();
                if (ParentalRules.controlled(s)) UsageService.start(this);
                refresh(v);
            });
            Look.Row picks = row(R.string.lumen_lp_child_apps).value(getString(R.string.lumen_lp_child_apps_count, s.allowed.size()));
            picks.setOnClickListener(v -> apps());
            row(R.string.lumen_lp_change_pin).setOnClickListener(v ->
                    startActivityForResult(PinActivity.forResult(this, PinActivity.CHANGE), ASK_CHANGE));
        }
        Look.Row eye = row(R.string.lumen_lp_eye).sub(getString(R.string.lumen_lp_eye_desc));
        eye.setFocusable(s.lockOn);
        list.getChildAt(0).requestFocus();
    }

    /** Re-draws the rows and keeps the focus on the row that changed. */
    private void refresh(View changed) {
        int at = list.indexOfChild(changed);
        rows();
        View v = list.getChildAt(Math.max(0, at));
        if (v != null) v.requestFocus();
    }

    private void confirmOff() {
        new AlertDialog.Builder(this).setTitle(R.string.lumen_lp_lock_off_title).setMessage(R.string.lumen_lp_lock_off_text)
                .setPositiveButton(R.string.lumen_lp_turn_off, (d, w) -> {
                    ParentalRules.lockOff(s);
                    save();
                    rows();
                })
                .setNegativeButton(R.string.lumen_lp_cancel, null).show();
    }

    private String minutes(int min) {
        if (min == 0) return getString(R.string.lumen_lp_no_limit);
        if (min < 60) return getString(R.string.lumen_lp_minutes, min);
        String h = min % 60 == 0 ? Integer.toString(min / 60) : String.format(Locale.getDefault(), "%.1f", min / 60f);
        return getString(R.string.lumen_lp_hours, h);
    }

    private static int next(int[] options, int now) {
        for (int i = 0; i < options.length; i++) if (options[i] == now) return options[(i + 1) % options.length];
        return options[0];
    }

    /** The apps of child mode: every launcher app with a check, the parent's picks are what child mode shows. */
    private void apps() {
        apps = true;
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(Collator.getInstance());
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;
            if (ParentalRules.HOME.equals(pkg) || pkg.equals(getPackageName())) continue;
            byLabel.put(String.valueOf(r.loadLabel(pm)) + "\u0000" + pkg, pkg);
        }
        for (java.util.Map.Entry<String, String> e : byLabel.entrySet()) {
            String pkg = e.getValue();
            if (list.findViewWithTag(pkg) != null) continue;
            Look.Row r = new Look.Row(this, e.getKey().substring(0, e.getKey().indexOf('\u0000'))).check(s.allowed.contains(pkg));
            r.setTag(pkg);
            r.setOnClickListener(v -> {
                if (!s.allowed.remove(pkg)) s.allowed.add(pkg);
                save();
                ((Look.Row) v).check(s.allowed.contains(pkg));
            });
            list.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && apps) {
            rows();
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
