// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import org.lumen.parental.ControlRange;
import org.lumen.parental.ParentalClient;
import org.lumen.parental.ParentalRules;
import org.lumen.parental.ParentalSettings;
import org.lumen.parental.ParentalState;
import org.lumen.projector.R;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Parental controls (settings.parental, General), the stock parent page of XGIMI Home (家长设置) as a page of the menu
 * on every model that has the row: the state is Lumen Home's (apps/core/lumen-parental ParentalProvider). With the
 * lock on it opens only after the password (PinPage). Rows in the stock order: child lock, control range, time per
 * session, time per day, password at power on, child mode at power on; then child mode, its apps, the password change
 * and the eye protection note. Turning the lock on sets a password first, turning it off asks the stock question.
 */
final class ParentalPage {
    /** What the page needs from the menu. Main thread. */
    interface Host {
        void push(Page p);

        void pop();

        void refresh(Page p, int focus);

        void confirm(CharSequence title, CharSequence message, CharSequence ok, Runnable yes);

        void failed();
    }

    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> new Thread(r, "lumen-parental-page"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context app;
    private final Host host;
    final Page page;
    private final Page apps;
    private ParentalState s = new ParentalState();

    ParentalPage(Context c, Host host) {
        app = c.getApplicationContext();
        this.host = host;
        page = new Page(s(R.string.lumen_lp_title));
        apps = new Page(s(R.string.lumen_lp_child_apps));
    }

    private String s(int res) { return app.getString(res); }

    /** OK on the row (or the old address): the state from Home, the password first while the lock is on. */
    void open() {
        read(st -> {
            if (st.lockOn) pin(PinPage.VERIFY, () -> {
                host.pop();
                show();
            });
            else show();
        });
    }

    private void show() {
        rows();
        page.setInitialFocus(-1);
        host.push(page);
    }

    private void read(Consumer<ParentalState> then) {
        BG.execute(() -> {
            ParentalState st = ParentalClient.read(app);
            MAIN.post(() -> {
                if (st == null) {
                    host.failed();
                    return;
                }
                s = st;
                then.accept(st);
            });
        });
    }

    /** One write to Home, then the rows again from what Home stored. */
    private void write(String key, String value) {
        BG.execute(() -> {
            boolean ok = ParentalClient.set(app, key, value);
            MAIN.post(() -> {
                if (!ok) host.failed();
                reload();
            });
        });
    }

    private void reload() {
        read(st -> {
            rows();
            host.refresh(page, -1);
        });
    }

    private void pin(int mode, Runnable done) {
        PinPage[] p = new PinPage[1];
        p[0] = new PinPage(app, mode, new PinPage.Store() {
            @Override public void check(String keys, Consumer<Boolean> ok) {
                BG.execute(() -> {
                    boolean r = ParentalClient.checkPin(app, keys);
                    MAIN.post(() -> ok.accept(r));
                });
            }

            @Override public void set(String keys, Consumer<Boolean> ok) {
                BG.execute(() -> {
                    boolean r = ParentalClient.setPin(app, keys);
                    MAIN.post(() -> ok.accept(r));
                });
            }
        }, done, at -> { if (p[0] != null) host.refresh(p[0].page, at); });
        host.push(p[0].page);
    }

    private void rows() {
        page.clear();
        page.add(new ToggleRow(app, s(R.string.lumen_lp_lock), (row, on) -> {
            if (on && !s.lockOn) pin(PinPage.SET, () -> {
                host.pop();
                reload();
            });
            else if (!on && s.lockOn) host.confirm(s(R.string.lumen_lp_lock_off_title), s(R.string.lumen_lp_lock_off_text),
                    s(R.string.lumen_lp_turn_off), () -> write(ParentalSettings.LOCK_OFF, ParentalSettings.ON));
        }).setChecked(s.lockOn));
        if (s.lockOn) {
            ControlRange[] ranges = ControlRange.values();
            page.add(new ChoiceRow(app, s(R.string.lumen_lp_range), new CharSequence[]{s(R.string.lumen_lp_range_child),
                    s(R.string.lumen_lp_range_global)}, (row, i) -> write(ParentalSettings.RANGE, ranges[i].token))
                    .setSelected(s.range.ordinal()).setCommitDelay(MenuController.CHOICE_COMMIT_MS));
            page.add(minutes(R.string.lumen_lp_session, ParentalState.SESSION_MIN, s.sessionMin, ParentalSettings.SESSION));
            page.add(minutes(R.string.lumen_lp_daily, ParentalState.DAILY_MIN, s.dailyMin, ParentalSettings.DAILY));
            page.add(flag(R.string.lumen_lp_boot_pin, s.bootPin, ParentalSettings.BOOT_PIN));
            page.add(flag(R.string.lumen_lp_boot_child, s.bootChild, ParentalSettings.BOOT_CHILD));
            page.add(flag(R.string.lumen_lp_child_mode, s.childMode, ParentalSettings.CHILD));
            page.add(new NavRow(app, s(R.string.lumen_lp_child_apps), this::showApps)
                    .setValue(app.getString(R.string.lumen_lp_child_apps_count, s.allowed.size())));
            page.add(new NavRow(app, s(R.string.lumen_lp_change_pin), () -> pin(PinPage.CHANGE, host::pop)));
        }
        page.add(new TextRow(app, s(R.string.lumen_lp_eye_desc)));
    }

    private ChoiceRow minutes(int title, int[] options, int now, String key) {
        CharSequence[] shown = new CharSequence[options.length];
        int at = 0;
        for (int i = 0; i < options.length; i++) {
            shown[i] = minutes(options[i]);
            if (options[i] == now) at = i;
        }
        return new ChoiceRow(app, s(title), shown, (row, i) -> write(key, Integer.toString(options[i])))
                .setSelected(at).setCommitDelay(MenuController.CHOICE_COMMIT_MS);
    }

    private String minutes(int min) {
        if (min == 0) return s(R.string.lumen_lp_no_limit);
        if (min < 60) return app.getString(R.string.lumen_lp_minutes, min);
        String h = min % 60 == 0 ? Integer.toString(min / 60) : String.format(Locale.getDefault(), "%.1f", min / 60f);
        return app.getString(R.string.lumen_lp_hours, h);
    }

    private ToggleRow flag(int title, boolean on, String key) {
        return new ToggleRow(app, s(title), (row, want) -> write(key, want ? ParentalSettings.ON : ParentalSettings.OFF))
                .setChecked(on);
    }

    /** The apps of child mode: every launcher app with a switch, the parent's picks are what child mode shows. */
    private void showApps() {
        apps.clear();
        PackageManager pm = app.getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(Collator.getInstance());
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;
            if (ParentalRules.HOME.equals(pkg) || pkg.equals(app.getPackageName()) || byLabel.containsValue(pkg)) continue;
            byLabel.put(r.loadLabel(pm) + "\u0000" + pkg, pkg);
        }
        for (java.util.Map.Entry<String, String> e : byLabel.entrySet()) {
            String pkg = e.getValue();
            apps.add(new ToggleRow(app, e.getKey().substring(0, e.getKey().indexOf('\u0000')), (row, want) -> {
                row.setChecked(want);
                if (want) s.allowed.add(pkg);
                else s.allowed.remove(pkg);
                write(want ? ParentalSettings.ALLOW : ParentalSettings.DENY, pkg);
            }).setChecked(s.allowed.contains(pkg)));
        }
        apps.setInitialFocus(-1);
        host.push(apps);
    }
}
