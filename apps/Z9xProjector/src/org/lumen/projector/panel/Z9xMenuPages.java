// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.audio.EqCurve;
import org.lumen.projector.audio.SoundEq;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.EqBandsRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Notify;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;

/**
 * Z9X: the device pages shown INSIDE the shared menu (MenuController.Pages), the ones the Z9X
 * 1.0.10 quick panel had: the input list (QpdSource), the professional picture pages (ProPicture), HDMI-CEC
 * (CecPage), the fit page (FitScreenPage, only where a fit device is supported) and the equalizer of the current
 * sound mode (the module's EqBandsRow; values per sound mode in audio.SoundEq). Built once per menu (the menu is
 * rebuilt on a locale change), except the fit page, built at every open so it shows the stored values.
 */
final class Z9xMenuPages implements MenuController.Pages {
    private static final String TAG = "Z9xMenu";
    /** Equalizer presets in EqCurve.PRESET_* order (Custom last). */
    static final String[] EQ_PRESET_LABELS = {"eq_preset_flat", "eq_preset_bass", "eq_preset_voice", "eq_preset_night",
            "eq_preset_custom"};
    private static final long EQ_LIMIT_NOTE_GAP_MS = 2_000;

    private final Context app;
    private final SharedPreferences prefs;
    private final Z9xFeatures dev;
    private MenuController menu;
    private QpdSource source;
    private ProPicture pro;
    private Page cec;
    // equalizer (QPC:1264)
    private Page eqPage;
    private TextRow eqHint;
    private ChoiceRow eqPreset;
    private EqBandsRow eqBands;
    private NavRow eqReset;
    private int eqMode = -1;
    private long eqLimitNoteAt;

    Z9xMenuPages(Context c, Z9xFeatures dev) {
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
        this.dev = dev;
        dev.setEqListener(this::applyEq);
    }

    private String s(int res) { return app.getString(res); }

    private String named(String name) { return dev.text(name); }

    @Override public Page page(FeatureId id, MenuController m) {
        menu = m;
        switch (id) {
            case INPUT: return source().page;
            case PICTURE_PRO: return pro().page;
            case SETTINGS_CEC: return cec();
            case KEYSTONE_FIT_SCREEN: return fitPage();
            case SOUND_EQ: return eq();
            default: return null;
        }
    }

    // ================================================================== input
    private QpdSource source() {
        if (source == null) {
            source = new QpdSource(app, s(R.string.panel_tile_input), new QpdSource.Host() {
                @Override public void close() { if (menu != null) menu.dismiss(); }
                @Override public boolean usbFilesAvailable() { return Z9xMenuPages.usbFilesAvailable(app); }
                @Override public void openUsbFiles() {
                    if (menu != null && menu.setupDone("usb files")) menu.closeThen(() -> Z9xMenuPages.openUsbFiles(app));
                }
            });
        }
        return source;
    }

    private static Intent usbFilesIntent() {
        return new Intent(Intent.ACTION_MAIN).setClassName("org.lumen.home", "org.lumen.home.FilesLauncher")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static boolean usbFilesAvailable(Context app) {
        try {
            return usbFilesIntent().resolveActivity(app.getPackageManager()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static void openUsbFiles(Context app) {
        try {
            Ui.wakeFromDream(app);
            app.startActivity(usbFilesIntent());
        } catch (Throwable t) {
            Log.w(TAG, "usb files: " + t);
            Ui.toast(app, R.string.lumen_m_toast_failed);
        }
    }

    // ================================================================== expert picture, CEC, fit
    private ProPicture pro() {
        if (pro == null) {
            pro = new ProPicture(app, prefs, new ProPicture.Host() {
                @Override public void push(Page pg) { if (menu != null) menu.push(pg); }
                @Override public void confirm(int titleRes, CharSequence msg, int okRes, Runnable onOk) {
                    if (menu != null) menu.confirm(titleRes, msg, okRes, onOk);
                }
            });
        }
        return pro;
    }

    private Page cec() {
        if (cec == null) {
            cec = org.lumen.projector.cec.CecPage.build(app, new org.lumen.projector.cec.CecPage.Host() {
                @Override public void refreshPage(Page page) { if (menu != null) menu.refreshPage(page); }
                @Override public void closeThen(Runnable r) { if (menu != null) menu.closeThen(r); }
                @Override public void openHdmiOptions() {
                    try {
                        Ui.wakeFromDream(app);
                        app.startActivity(new Intent().setClassName("org.lumen.tvinput", "org.lumen.tvinput.SettingsActivity")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable t) {
                        Log.w(TAG, "HDMI settings: " + t);
                        Ui.toast(app, R.string.lumen_m_toast_failed);
                    }
                }
                @Override public boolean setupDone(String what) { return menu == null || menu.setupDone(what); }
            });
        }
        return cec;
    }

    private Page fitPage() {
        return org.lumen.projector.fit.FitScreenPage.build(app, new org.lumen.projector.fit.FitScreenPage.Host() {
            @Override public void push(Page page) { if (menu != null) menu.push(page); }
            @Override public void refreshPage(Page page) { if (menu != null) menu.refreshPage(page); }
            @Override public void closeThen(Runnable r) { if (menu != null) menu.closeThen(r); }
        });
    }

    // ================================================================== equalizer (v6.4, not in stock; QPC:1264)
    /** The preset label of the current sound mode's equalizer when it is usable (AMP mode, a valid mode), else null. */
    static String eqPresetLabel(Context app, Z9xMenuRules.In in) {
        if (in == null || in.soundEffect == null || !SoundEq.validMode(in.soundEffect)) return null;
        if (!SoundState.eqUsable(in.soundProcess, in.dtsOn)) return null;
        int p = SoundEq.load(app, in.soundEffect).preset;
        return EQ_PRESET_LABELS[Math.max(0, Math.min(EQ_PRESET_LABELS.length - 1, p))];
    }

    private Page eq() {
        if (eqPage != null) return eqPage;
        eqPage = new Page(named("eq_title"));
        eqPage.setOnShown(() -> { if (menu != null) menu.refresh(); });
        eqHint = eqPage.add(new TextRow(app, ""));
        CharSequence[] presetLabels = new CharSequence[EQ_PRESET_LABELS.length];
        for (int i = 0; i < presetLabels.length; i++) presetLabels[i] = named(EQ_PRESET_LABELS[i]);
        eqPreset = eqPage.add(new ChoiceRow(app, named("eq_preset"), presetLabels, (row, i) -> safe("eq preset", () -> {
            if (!SoundEq.validMode(eqMode) || i < 0 || i > EqCurve.PRESET_CUSTOM) return;
            SoundEq.savePreset(app, eqMode, i);
            eqBands.setValues(SoundEq.load(app, eqMode).gains());
            SoundEq.requestUserApply();
        })));
        eqPreset.setCommitDelay(300);
        String[] bandLabels = new String[EqCurve.BANDS];
        for (int b = 0; b < EqCurve.BANDS; b++) bandLabels[b] = EqCurve.freqLabel(b);
        eqBands = eqPage.add(new EqBandsRow(app, bandLabels, EqCurve.MIN_HALF, EqCurve.MAX_HALF, new EqBandsRow.Listener() {
            @Override public int onChange(EqBandsRow row, int band, int wanted) {
                int[] v = row.getValues();
                int old = v[band];
                v[band] = wanted;
                // the COMBINED curve stays <= +6 dB (7 bands at +6 each would add up to +8.7 dB)
                if (wanted > old && !EqCurve.withinCap(v)) {
                    long now = SystemClock.uptimeMillis();
                    if (now - eqLimitNoteAt > EQ_LIMIT_NOTE_GAP_MS) {
                        eqLimitNoteAt = now;
                        Notify.show(app, named("eq_title"), named("eq_limit"));
                    }
                    return old;
                }
                eqPreset.setSelected(EqCurve.matchPreset(v));
                return wanted;
            }
            @Override public void onLimit(EqBandsRow row, int band, boolean up) {
                // 60 Hz stops at +3 dB (not +6): say why instead of silently ignoring UP
                if (!up || band != 0 || row.getValues()[0] < EqCurve.MAX_HALF[0]) return;
                long now = SystemClock.uptimeMillis();
                if (now - eqLimitNoteAt > EQ_LIMIT_NOTE_GAP_MS) {
                    eqLimitNoteAt = now;
                    Notify.show(app, named("eq_title"), named("eq_bass_limit"));
                }
            }
            @Override public void onCommit(EqBandsRow row) {
                safe("eq commit", () -> {
                    if (!SoundEq.validMode(eqMode)) return;
                    SoundEq.saveCustom(app, eqMode, row.getValues());
                    eqPreset.setSelected(SoundEq.load(app, eqMode).preset);
                    SoundEq.requestUserApply();
                });
            }
        }));
        eqBands.setFormatter(v -> EqCurve.label(v) + " " + named("eq_db"));
        eqBands.setHints(named("eq_hint_select"), named("eq_hint_adjust"));
        eqReset = eqPage.add(new NavRow(app, named("eq_reset"), () -> safe("eq reset", () -> {
            if (!SoundEq.validMode(eqMode)) return;
            SoundEq.saveCustom(app, eqMode, new int[EqCurve.BANDS]);
            SoundEq.savePreset(app, eqMode, EqCurve.PRESET_FLAT);
            eqBands.setValues(new int[EqCurve.BANDS]);
            eqPreset.setSelected(EqCurve.PRESET_FLAT);
            SoundEq.requestUserApply();
            Notify.show(app, named("eq_title"), named("eq_reset_done"));
        })));
        eqReset.setChevron(false);
        applyEq(dev.last());
        return eqPage;
    }

    /** Main thread, after every read (QPC:1330 applyEq): the rows for the current sound mode and process. */
    private void applyEq(Z9xMenuRules.In in) {
        if (eqPage == null) return;
        boolean ok = in != null;
        int mode = ok && in.soundEffect != null && SoundEq.validMode(in.soundEffect) ? in.soundEffect : -1;
        boolean amp = ok && SoundState.eqUsable(in.soundProcess, in.dtsOn);
        boolean dts = ok && SoundState.dtsStored(in.soundProcess, in.dtsOn);
        boolean usable = amp && mode >= 0;
        if (!(eqBands.isBusy() && mode == eqMode)) {                 // the user is adjusting: keep the rows as they are
            eqMode = usable ? mode : -1;
            SoundEq.Setting st = usable ? SoundEq.load(app, mode) : null;
            eqPreset.setSelected(st != null ? st.preset : -1);
            eqBands.setValues(st != null ? st.gains() : null);
        }
        eqPreset.setRowEnabled(usable);
        eqBands.setRowEnabled(usable);
        eqReset.setRowEnabled(usable);
        CharSequence hint;
        if (!ok) hint = named("toast_not_ready");
        else if (dts) hint = named("eq_dts_note");
        else if (!amp) hint = named("sound_unknown_hint");
        else if (mode < 0) hint = named("eq_no_mode");
        else if (in.soundPathActive && in.soundPath != Z9xMenuRules.SPEAKER_PATH) hint = named("eq_speaker_only");
        else hint = dev.text("eq_hint_mode", named(Z9xMenuRules.soundLabelRes(mode)));
        eqHint.setText(hint);
        if (menu != null) menu.refreshPage(eqPage);
    }

    private static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.e(TAG, what + " failed", t);
        }
    }
}
