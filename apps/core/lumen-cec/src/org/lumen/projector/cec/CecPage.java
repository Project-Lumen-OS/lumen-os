// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.cec;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;

import org.lumen.cec.CecDriver;
import org.lumen.cec.CecRow;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * The HDMI-CEC page of every projector (module lumen-cec), shown inside the shared menu (settings.cec): one place for
 * every CEC behaviour, each row backed by its single owner. The projector's {@link CecDriver} names its rows (the ones
 * its stock has) and owns the two power switches (HDMI-CEC on, turn on with an HDMI device); the framework settings
 * are CecSettings, the HDMI app's switches its provider. Values are read on the driver's worker each time the page is
 * shown and after every change (read-back); rows show "pending" until then. Built by the device's menu pages (main
 * thread), which supply the {@link Host}.
 */
public final class CecPage {
    private static final String TAG = "Z9xCec";
    private static final String TVINPUT_PKG = "org.lumen.tvinput";
    private static final String TVINPUT_VIEWER = "org.lumen.tvinput.PassthroughActivity";
    /** org.lumen.tvinput's port inputs: ".../HW&lt;deviceId&gt;", deviceId == HDMI port id. */
    private static final String PORT_INPUT_PREFIX = "org.lumen.tvinput/.HdmiInputService/HW";
    private static final long NIGHT_COMMIT_MS = 500;

    /** What the page needs from the menu. Main thread. */
    public interface Host {
        /** Re-render the page if it is the one shown (rows were added / removed). */
        void refreshPage(Page page);
        /** Close the panel, then run r (opening another window). */
        void closeThen(Runnable r);
        /** The menu's HDMI page (auto switch, return home, the source at power-on, the ports). */
        void openHdmiOptions();
        /** First-run gate (toast + false while setup runs). */
        boolean setupDone(String what);
    }

    private final Context app;
    private final Host host;
    private final CecDriver driver;
    private final Set<CecRow> shown;
    private final Page page;
    private final ToggleRow master, wake, sendStandby, soundbar, avrOn, otp, control, internalExit;
    private final ChoiceRow night;
    private final TextRow paused, unavailable, name;
    private final HeaderRow devicesHeader;
    private final List<View> deviceRows = new ArrayList<>();
    /** Device list shown now (rows are rebuilt only when it changes). */
    private String deviceSig;
    /** Our own refreshPage re-renders the page, which runs onShown again: that one load is skipped. */
    private boolean skipNextLoad;

    /** Values read on the worker for one apply on the main thread. */
    private static final class Values {
        Boolean master, wake, sendStandby, soundbar, otp, control, internalExit;
        boolean avrOn, paused;
        int night;
        String deviceName;
        List<CecDevices.Dev> devices;
    }

    /** A switch's write on the worker: false = failed (toast). */
    private interface Write { boolean set(boolean on); }

    public static Page build(Context app, Host host, CecDriver driver) {
        return new CecPage(app.getApplicationContext(), host, driver).page;
    }

    private CecPage(Context app, Host host, CecDriver driver) {
        this.app = app;
        this.host = host;
        this.driver = driver;
        shown = driver.rows();
        driver.install(app);
        page = new Page(s(R.string.lumen_cec_title));
        unavailable = page.add(new TextRow(app, s(R.string.lumen_cec_unavailable)));
        unavailable.setVisibility(View.GONE);
        master = page.add(new ToggleRow(app, s(R.string.lumen_cec_master), (row, w) ->
                change(CecRow.MASTER, () -> driver.setMaster(app, w))));
        note(CecRow.MASTER_NOTE, R.string.lumen_cec_master_note);
        wake = toggle(CecRow.WAKE, R.string.lumen_cec_wake, w -> driver.setWake(app, w));
        note(CecRow.WAKE_NOTE, R.string.lumen_cec_wake_note);
        if (shown.contains(CecRow.NIGHT)) {
            night = page.add(new ChoiceRow(app, s(R.string.lumen_cec_night), nightLabels(), (row, i) ->
                    run(() -> CecSettings.setNightChoice(app, i))));
            night.setCommitDelay(NIGHT_COMMIT_MS);
            page.add(new TextRow(app, s(R.string.lumen_cec_night_desc)));
            paused = page.add(new TextRow(app, s(R.string.lumen_cec_paused)));
            paused.setVisibility(View.GONE);
        } else {
            night = null;
            paused = null;
        }
        otp = toggle(CecRow.SWITCH, R.string.lumen_cec_switch, w -> CecSettings.setTvinputPref(app, CecSettings.TV_OTP, w));
        sendStandby = toggle(CecRow.SEND_STANDBY, R.string.lumen_cec_send_standby, w -> CecSettings.setSendStandby(app, w));
        if (sendStandby != null) page.add(new TextRow(app, s(R.string.lumen_cec_follow_info)));
        soundbar = toggle(CecRow.SOUNDBAR, R.string.lumen_cec_soundbar, w -> CecSettings.setSoundbar(app, w));
        avrOn = toggle(CecRow.SOUNDBAR_ON, R.string.lumen_cec_avr_on, w -> { CecSettings.setAvrOn(app, w); return true; });
        control = toggle(CecRow.REMOTE, R.string.lumen_cec_control,
                w -> CecSettings.setTvinputPref(app, CecSettings.TV_CONTROL, w));
        internalExit = toggle(CecRow.LEAVE_NOTICE, R.string.lumen_cec_internal_exit,
                w -> CecSettings.setTvinputPref(app, CecSettings.TV_INTERNAL_ON_EXIT, w));
        if (shown.contains(CecRow.DEVICES)) {
            devicesHeader = page.add(new HeaderRow(app, s(R.string.lumen_cec_devices)));
            name = page.add(new TextRow(app, ""));
        } else {
            devicesHeader = null;
            name = null;
        }
        if (shown.contains(CecRow.HDMI_OPTIONS)) {
            page.add(new NavRow(app, s(R.string.lumen_m_hdmi_options), () -> {
                if (host.setupDone("hdmi options")) host.openHdmiOptions();
            }));
        }
        page.setOnShown(this::load);
    }

    private String s(int res) {
        return app.getString(res);
    }

    /** The switch of {@code r} when this projector shows it, else null. */
    private ToggleRow toggle(CecRow r, int label, Write write) {
        if (!shown.contains(r)) return null;
        return page.add(new ToggleRow(app, s(label), (row, w) -> change(r, () -> write.set(w))));
    }

    /** The text row of {@code r} when this projector shows it. */
    private void note(CecRow r, int text) {
        if (shown.contains(r)) page.add(new TextRow(app, s(text)));
    }

    private CharSequence[] nightLabels() {
        CharSequence[] out = new CharSequence[CecSettings.NIGHT_CHOICES.length];
        java.text.DateFormat f = DateFormat.getTimeFormat(app);
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < out.length; i++) {
            int[] r = CecSettings.NIGHT_CHOICES[i];
            if (r[0] < 0) {
                out[i] = s(R.string.lumen_cec_night_off);
                continue;
            }
            cal.set(2000, Calendar.JANUARY, 1, r[0], 0, 0);
            String from = f.format(cal.getTime());
            cal.set(2000, Calendar.JANUARY, 1, r[1], 0, 0);
            String to = f.format(cal.getTime());
            out[i] = app.getString(R.string.lumen_cec_night_range, from, to);
        }
        return out;
    }

    // =================================================================== changes

    private interface Setter { boolean set(); }

    /**
     * A toggle asked for a change: write on the worker, then read everything back. A change that went through is the
     * user's choice of that row (CecStockDefaults keeps it).
     */
    private void change(CecRow r, Setter s) {
        run(() -> {
            boolean ok = s.set();
            if (ok) CecSettings.setUserChose(app, r);
            else Ui.main().post(() -> Ui.toast(app, R.string.lumen_m_toast_failed));
        });
    }

    private void run(Runnable r) {
        driver.worker().execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                Log.w(TAG, "page change: " + t);
            }
            readAndApply();
        });
    }

    // =================================================================== load / apply

    private void load() {
        if (skipNextLoad) {
            skipNextLoad = false;
            return;
        }
        driver.worker().execute(this::readAndApply);
    }

    /** Worker. */
    private void readAndApply() {
        final Values v = new Values();
        v.master = driver.masterOn(app);
        if (wake != null) v.wake = driver.wakeOn(app);
        if (sendStandby != null) v.sendStandby = CecSettings.sendStandbyOn(app);
        if (soundbar != null) v.soundbar = CecSettings.soundbarOn(app);
        if (avrOn != null) v.avrOn = CecSettings.avrOn(app);
        if (night != null) {
            v.night = CecSettings.nightChoice(app);
            v.paused = driver.wakePaused();
        }
        if (otp != null || control != null || internalExit != null) {
            Bundle tp = CecSettings.tvinputPrefs(app);
            if (tp != null) {
                v.otp = pref(tp, CecSettings.TV_OTP);
                v.control = pref(tp, CecSettings.TV_CONTROL);
                v.internalExit = pref(tp, CecSettings.TV_INTERNAL_ON_EXIT);
            }
        }
        if (devicesHeader != null) {
            try {
                v.deviceName = Settings.Global.getString(app.getContentResolver(), Settings.Global.DEVICE_NAME);
            } catch (Throwable t) {
                v.deviceName = null;
            }
            v.devices = Boolean.TRUE.equals(v.master) ? CecDevices.read(app) : new ArrayList<>();
        }
        driver.afterRead(app);
        Ui.main().post(() -> apply(v));
    }

    private static Boolean pref(Bundle b, String key) {
        return b.containsKey(key) ? b.getBoolean(key) : null;
    }

    /** Main thread. */
    private void apply(Values v) {
        boolean on = Boolean.TRUE.equals(v.master);
        unavailable.setVisibility(v.master == null ? View.VISIBLE : View.GONE);
        master.setChecked(v.master);
        master.setRowEnabled(v.master != null);
        check(wake, v.wake);
        if (night != null) night.setSelected(v.night);
        check(otp, v.otp);
        check(sendStandby, v.sendStandby);
        check(soundbar, v.soundbar);
        check(avrOn, v.avrOn);
        check(control, v.control);
        check(internalExit, v.internalExit);
        boolean usable = on || !driver.rowsNeedMaster();
        for (View r : new View[]{wake, night, otp, sendStandby, soundbar, control, internalExit}) {
            if (r instanceof Row) ((Row) r).setRowEnabled(usable);
        }
        if (avrOn != null) avrOn.setRowEnabled(usable && Boolean.TRUE.equals(v.soundbar));
        if (paused != null) paused.setVisibility(on && v.paused ? View.VISIBLE : View.GONE);
        if (devicesHeader == null) return;
        name.setText(app.getString(R.string.lumen_cec_name_on_hdmi,
                TextUtils.isEmpty(v.deviceName) ? android.os.Build.MODEL : v.deviceName));
        if (rebuildDevices(on ? v.devices : null)) {
            skipNextLoad = true;
            host.refreshPage(page);
            // not shown (panel closed meanwhile): no render, so no onShown to skip
            Ui.main().post(() -> skipNextLoad = false);
        }
    }

    private static void check(ToggleRow row, Boolean value) {
        if (row != null) row.setChecked(value);
    }

    /** True when the device rows changed (the page must be re-rendered). */
    private boolean rebuildDevices(List<CecDevices.Dev> devs) {
        StringBuilder sig = new StringBuilder();
        if (devs != null) {
            for (CecDevices.Dev d : devs) {
                sig.append(d.id).append('/').append(d.port).append('/').append(d.power).append('/')
                        .append(d.name).append('/').append(d.inputId).append(';');
            }
        }
        String now = sig.toString();
        if (now.equals(deviceSig)) return false;
        deviceSig = now;
        List<View> rows = page.rows();
        for (View r : deviceRows) rows.remove(r);
        deviceRows.clear();
        int at = rows.indexOf(devicesHeader) + 1;
        if (devs == null || devs.isEmpty()) {
            deviceRows.add(new TextRow(app, s(R.string.lumen_cec_no_devices)));
        } else {
            for (CecDevices.Dev d : devs) deviceRows.add(deviceRow(d));
        }
        rows.addAll(at, deviceRows);
        return true;
    }

    private View deviceRow(CecDevices.Dev d) {
        String title = !d.name.isEmpty() ? d.name
                : s(d.isAudioSystem() ? R.string.lumen_cec_dev_soundbar : R.string.lumen_cec_dev_unnamed);
        String state = d.power == android.hardware.hdmi.HdmiControlManager.POWER_STATUS_ON
                || d.power == android.hardware.hdmi.HdmiControlManager.POWER_STATUS_TRANSIENT_TO_ON
                ? s(R.string.lumen_cec_state_on)
                : d.power == android.hardware.hdmi.HdmiControlManager.POWER_STATUS_STANDBY
                || d.power == android.hardware.hdmi.HdmiControlManager.POWER_STATUS_TRANSIENT_TO_STANDBY
                ? s(R.string.lumen_cec_state_standby) : "";
        String value = state.isEmpty() ? app.getString(R.string.lumen_cec_dev_port, d.port)
                : app.getString(R.string.lumen_cec_dev_value, d.port, state);
        NavRow row = new NavRow(app, title);
        row.setValue(value);
        if (!d.isSource()) {
            row.setChevron(false);
            return row;
        }
        final String input = d.inputId != null ? d.inputId : PORT_INPUT_PREFIX + d.port;
        row.setOnClick(() -> {
            if (host.setupDone("cec device")) host.closeThen(() -> openInput(input));
        });
        return row;
    }

    private void openInput(String inputId) {
        try {
            Ui.wakeFromDream(app);
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
                    .setClassName(TVINPUT_PKG, TVINPUT_VIEWER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
            Log.i(TAG, "panel: open " + inputId);
        } catch (Throwable t) {
            Log.w(TAG, "open " + inputId + ": " + t);
            Ui.toast(app, R.string.lumen_m_toast_failed);
        }
    }
}
