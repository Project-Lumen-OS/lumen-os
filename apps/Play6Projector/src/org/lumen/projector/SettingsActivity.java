package org.lumen.projector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.battery.Battery;
import org.lumen.projector.battery.BatteryNotices;
import org.lumen.projector.diag.Probe;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.settings.Kit;
import org.lumen.projector.settings.Shell;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Projector" settings of the XGIMI Play 6 (quick panel > All settings > Projector settings). A fork of
 * Z9xProjector SettingsActivity.java (tools/play6/forks.tsv): framework widgets only, D-pad friendly, the
 * same sections with the Play 6 rows:
 *  - Focus: Autofocus now, Manual focus; Keystone: Auto keystone now;
 *  - Automation: auto keystone at power-on (553 / 552), auto keystone after moving = real-time keystone
 *    (586 / 585), smart autofocus (IGmpf2 63, then 64 + IGmpf 587 through Hal.setSmartAutofocus; hidden
 *    while the IGmpf2 guard is closed);
 *  - Remote keys (app slots, pair a remote) and the home screen choice (shared code);
 *  - Diagnostics, read only and only on a test build (ro.lumen.version play6-t...;
 *    battery_sound_features.md 6.4: the section and its reads do not exist on any other build): the start
 *    gate, IGmpf / IGmpf2 and the guard, the recorded and live hash chains (the live IGmpf chain is not
 *    read: the HAL check allows the IBase code only in Play6Gmpf2), 588, 402, 438, the battery raw values (240 /
 *    238 / 239 / 327, IGmpf2 52 / 51 / 46 / 47) next to the battery state (its watchdog, age and agreed
 *    state, battery/Battery), the vendor power listener (235 / 242) and the cards (battery/BatteryNotices),
 *    212, the focus motor and the 404 feature list (on p6-long, with support_battery next to
 *    Device.hasBattery()).
 * Lumen OS 1.0.9 look (decision 2026-11-10, Q1: the Z9X change of 0e3f9252 ported): sections in a rail on
 * the left, the "now" actions as large tiles, settings in cards (settings.Kit, settings.Shell, taken from
 * apps/Z9xProjector by the recipe); the rows, HAL calls and rules of the Play 6 stay as they were.
 * All HAL I/O goes through the Hal facade (p6-hal / p6-long); results come back on the main thread.
 * Toggles are read on open and written ONLY on click. Not on the Play 6: interface resolution, problem
 * reports, fit to screen and obstacle avoidance, autofocus at power-on.
 */
public final class SettingsActivity extends Activity {
    private static final String TAG = "P6Settings";
    static final String EXTRA_SECTION = "org.lumen.projector.extra.SECTION";
    static final String SECTION_KEYSTONE = "keystone";

    private Shell shell;
    private View keystoneAnchor;
    private final Map<AutomationToggle, Kit.Row> toggleRows = new EnumMap<>(AutomationToggle.class);
    private final Map<AutomationToggle, Boolean> toggleState = new EnumMap<>(AutomationToggle.class);
    private Kit.Row slot3Row, slot4Row, launcherRow, bootSourceRow, cecWakeRow;
    private Kit.Row voiceLang, voiceSpeak, voiceKey;
    private LinearLayout diagBox;
    private int diagGen;

    static void open(Context ctx, String section) {
        try {
            Intent i = new Intent(ctx, SettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (section != null) i.putExtra(EXTRA_SECTION, section);
            Ui.wakeFromDream(ctx);
            ctx.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "open: " + t);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        focusSection(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        focusSection(intent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (shell != null && shell.handleKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshToggles();
        refreshTemperature();
        refreshSlots();
        refreshDiagnostics();
    }

    // ------------------------------------------------------------------ UI building
    private void buildUi() {
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String lumen = SystemProperties.get("ro.lumen.version", "");
        CharSequence subline = lumen.isEmpty() ? getString(R.string.version_line, versionName())
                : getString(R.string.s2_brand_line, model, lumen);
        shell = new Shell(this, getString(R.string.app_name), subline);

        // Focus and keystone: the "now" actions as tiles (no fit to screen on the Play 6), the automation in cards
        LinearLayout focus = shell.section("focus", R.drawable.ic_s2_focus, getString(R.string.s2_sec_focus));
        Kit.heading(focus, getString(R.string.s2_now), getString(R.string.s2_now_title));
        View af = tile(R.drawable.ic_s2_t_af, R.string.s2_t_af, R.string.s2_t_af_sub, v -> Hal.requestAutofocus(this));
        keystoneAnchor = tile(R.drawable.ic_s2_t_kst, R.string.s2_t_kst, R.string.s2_t_kst_sub, v -> Hal.requestKeystone(this));
        View manual = tile(R.drawable.ic_s2_t_manual, R.string.s2_t_manual, R.string.s2_t_manual_sub,
                v -> ManualFocusActivity.open(this));
        focus.addView(Kit.tileRow(this, af, keystoneAnchor, manual));
        focus.addView(Kit.space(this, 20));
        LinearLayout left = Kit.card(this), right = Kit.card(this);
        toggle(left, AutomationToggle.POWER_ON_AK, R.string.tg_power_on_ak, R.string.s2_d_power_on_ak);       // 553 / 552
        toggle(right, AutomationToggle.MOVE_AK, R.string.tg_move_ak, R.string.s2_d_move);                      // 586 / 585
        toggle(right, AutomationToggle.SMART_AF, R.string.panel_smart_af, 0);       // IGmpf2 63 / 64 + 587, hidden while closed
        focus.addView(Kit.columns(this, left, right));

        // Power on: stock parity 2026-10-21 (General > Advanced > Power on / off > boot music, IGmpf 420 / 419;
        // Signal > Boot source; Signal > HDMI device controls the projector, cec/CecPolicy, shown once read; General >
        // About > Device info > temperature, IGmpf 438, read only, shown once 438 answered: rule)
        LinearLayout power = shell.section("power", R.drawable.ic_s2_power, getString(R.string.s2_sec_power));
        Kit.heading(power, null, getString(R.string.s2_sec_power));
        LinearLayout pc = Kit.card(this);
        toggle(pc, AutomationToggle.BOOT_MUSIC, R.string.tg_power_on_music, R.string.s2_d_music);
        bootSourceRow = valueRow(pc, R.string.p6_boot_source, R.string.s2_d_boot, v -> pickBootSource());
        refreshBootSource();
        cecWakeRow = valueRow(pc, R.string.p6_cec_wake, 0, v -> toggleCecWake());
        cecWakeRow.setShown(false);
        refreshCecWake();
        tempRow = valueRow(pc, R.string.p6_temperature, 0, v -> refreshTemperature());
        tempRow.setShown(false);
        power.addView(pc);

        // Remote and keys
        LinearLayout remote = shell.section("remote", R.drawable.ic_s2_remote, getString(R.string.s2_sec_remote));
        Kit.heading(remote, null, getString(R.string.s2_sec_remote));
        LinearLayout rc = Kit.card(this);
        slot3Row = valueRow(rc, R.string.key_slot3, R.string.s2_d_slot, v -> pickApp(3));
        slot4Row = valueRow(rc, R.string.key_slot4, R.string.s2_d_slot, v -> pickApp(4));
        // Pair a new / second / reset remote (120 s fast LE scan; RemoteAutoPair.startPairing).
        actionRow(rc, R.string.remote_pair_action, R.string.s2_d_pair,
                v -> org.lumen.projector.remote.RemoteAutoPair.startPairing(getApplicationContext()));
        remote.addView(rc);

        // Lumen OS 1.0.5: the voice assistant (mic key), only where a speech recogniser exists
        if (org.lumen.projector.voice.Assistant.available(this)) {
            LinearLayout voice = shell.section("voice", R.drawable.ic_s2_mic, getString(R.string.voice_settings));
            Kit.heading(voice, null, getString(R.string.voice_settings));
            LinearLayout vc = Kit.card(this);
            voiceLang = valueRow(vc, R.string.voice_language, R.string.s2_d_voice_lang,
                    v -> org.lumen.projector.voice.VoiceSettings.pickLanguage(this, this::refreshVoice));
            voiceSpeak = valueRow(vc, R.string.voice_speak, R.string.s2_d_voice_speak,
                    v -> org.lumen.projector.voice.VoiceSettings.toggleSpeak(this, this::refreshVoice));
            voiceKey = valueRow(vc, R.string.voice_key, R.string.s2_d_voice_key,
                    v -> org.lumen.projector.voice.VoiceSettings.editKey(this, this::refreshVoice));
            voice.addView(vc);
            refreshVoice();
        }

        // Home screen: the way back to Lumen Home in classic mode (gear long press -> Projector)
        LinearLayout home = shell.section("home", R.drawable.ic_s2_home, getString(R.string.s2_sec_home));
        Kit.heading(home, null, getString(R.string.s2_sec_home));
        LinearLayout hc = Kit.card(this);
        if (org.lumen.projector.home.LauncherSwitcher.choiceAvailable(this)) {
            launcherRow = valueRow(hc, R.string.launcher_row, R.string.s2_d_launcher, v -> pickLauncher());
        }
        if (hc.getChildCount() > 0) home.addView(hc);
        shell.hideIfEmpty(home);

        // Help: licences of third party material in this app (the shared menu module carries the game menu controller
        // models, which need a CC BY credit, also where the model has no game menu); the Z9X row of 2026-11-11
        LinearLayout help = shell.section("help", R.drawable.ic_s2_help, getString(R.string.s2_sec_help));
        Kit.heading(help, null, getString(R.string.s2_sec_help));
        LinearLayout gc = Kit.card(this);
        actionRow(gc, R.string.lumen_gm_licences, R.string.lumen_gm_licences_d, v -> org.lumen.projector.panel.GmLicences.show(this));
        // anonymous statistics (apps/core/lumen-stats): this app keeps the switch, the Play 6 image has no updater
        if (org.lumen.stats.Ping.configured()) {
            org.lumen.stats.PrefsState stats = org.lumen.stats.PrefsState.get(this);
            Kit.Row sr = Kit.switchRow(this, getString(R.string.lumen_stats_row), getString(R.string.lumen_stats_note));
            sr.onClick(v -> {
                stats.setOn(!stats.on());
                sr.sw.setOn(stats.on());
            });
            Kit.add(gc, sr);
            sr.sw.setOn(stats.on());
        }
        help.addView(gc);

        if (Device.isTestBuild()) {                     // Diagnostics: test builds only
            LinearLayout diag = shell.section("diag", R.drawable.ic_s2_diag, getString(R.string.sec_diag));
            Kit.heading(diag, null, getString(R.string.sec_diag));
            LinearLayout dc = Kit.card(this);
            actionRow(dc, R.string.act_refresh, R.string.s2_d_refresh, v -> refreshDiagnostics());
            diag.addView(dc);
            diag.addView(Kit.space(this, 14));
            diagBox = Kit.card(this);
            diagBox.setPadding(Kit.dp(this, 4), Kit.dp(this, 8), Kit.dp(this, 4), Kit.dp(this, 8));
            diag.addView(diagBox);
        }

        shell.attach(getString(R.string.s2_hint_ok), getString(R.string.s2_hint_nav));
    }

    private View tile(int icon, int title, int sub, View.OnClickListener l) {
        return Kit.tile(this, icon, getString(title), getString(sub), l);
    }

    /** sub 0: a Play 6 row without an explanation line (no new translations for it yet). */
    private Kit.Row valueRow(LinearLayout card, int title, int sub, View.OnClickListener l) {
        Kit.Row r = Kit.valueRow(this, getString(title), sub == 0 ? null : getString(sub)).onClick(l);
        Kit.add(card, r);
        return r;
    }

    private Kit.Row actionRow(LinearLayout card, int title, int sub, View.OnClickListener l) {
        Kit.Row r = Kit.actionRow(this, getString(title), sub == 0 ? null : getString(sub)).onClick(l);
        Kit.add(card, r);
        return r;
    }

    private void toggle(LinearLayout card, AutomationToggle t, int title, int sub) {
        Kit.Row r = Kit.switchRow(this, getString(title), sub == 0 ? null : getString(sub)).onClick(v -> onToggleClick(t));
        Kit.add(card, r);
        r.sw.setOn(null);
        toggleRows.put(t, r);
    }

    private void refreshVoice() {
        if (voiceLang == null) return;
        voiceLang.value.setText(org.lumen.projector.voice.VoiceSettings.languageValue(this));
        voiceSpeak.value.setText(org.lumen.projector.voice.VoiceSettings.speakValue(this));
        voiceKey.value.setText(org.lumen.projector.voice.VoiceSettings.keyValue(this));
    }

    private void focusSection(Intent i) {
        String s = i == null ? null : i.getStringExtra(EXTRA_SECTION);
        View target = null;
        if (SECTION_KEYSTONE.equals(s)) target = keystoneAnchor;
        if (target != null && target.isFocusable()) shell.focusView(target);
        else shell.focusSection(null);
    }

    // ------------------------------------------------------------------ toggles
    private void refreshToggles() {
        Hal.query(SettingsActivity::readToggles, this::showToggles);
    }

    /** p6-hal: every toggle (null = unknown); SMART_AF only while the IGmpf2 guard is open. */
    private static Map<AutomationToggle, Boolean> readToggles(Play6Gmpf g, Play6Gmpf2 g2) {
        Map<AutomationToggle, Boolean> m = new EnumMap<>(AutomationToggle.class);
        for (AutomationToggle t : AutomationToggle.values()) {
            try {
                switch (t) {
                    case POWER_ON_AK: m.put(t, g.getPowerOnAk()); break;          // 553
                    case MOVE_AK: m.put(t, g.getMoveAk()); break;                 // 586
                    case SMART_AF: m.put(t, g2.isIdentityOpen() ? g2.getSmartAutofocus() : null); break;   // IGmpf2 63
                    case BOOT_MUSIC: m.put(t, g.getPowerOnMusic()); break;           // 420
                    default: break;
                }
            } catch (Throwable e) {
                Log.w(TAG, "get " + t + ": " + e);
                m.put(t, null);
            }
        }
        return m;
    }

    private void showToggles(Map<AutomationToggle, Boolean> m) {
        if (isFinishing() || isDestroyed()) return;
        Kit.Row smart = toggleRows.get(AutomationToggle.SMART_AF);
        if (smart != null) smart.setShown(Hal.hasGmpf2());
        Kit.Row music = toggleRows.get(AutomationToggle.BOOT_MUSIC);            // hidden when 420 does not answer (rule)
        if (music != null) music.setShown(m != null && m.get(AutomationToggle.BOOT_MUSIC) != null);
        for (AutomationToggle t : AutomationToggle.values()) {
            Boolean v = m == null ? null : m.get(t);
            toggleState.put(t, v);
            Kit.Row r = toggleRows.get(t);
            if (r != null) r.sw.setOn(v);         // null: not read (HAL not ready), the switch shows dim
        }
    }

    private void onToggleClick(AutomationToggle t) {
        Boolean cur = toggleState.get(t);
        if (cur == null || !Hal.isReady()) {
            Ui.toast(this, R.string.toast_not_ready);
            refreshToggles();
            return;
        }
        final boolean on = !cur;
        Kit.Row r = toggleRows.get(t);
        if (r != null) r.sw.setOn(null);
        toggleState.put(t, null);                       // no second click until the read-back
        final Context app = getApplicationContext();
        boolean queued = Hal.run((g, g2) -> {
            try {
                switch (t) {
                    case POWER_ON_AK: g.setPowerOnAk(on); break;                // 552
                    case MOVE_AK: g.setMoveAk(on); break;                       // 585
                    case SMART_AF: Hal.setSmartAutofocus(g, g2, on); break;     // IGmpf2 64, then 587
                    case BOOT_MUSIC: g.setPowerOnMusic(on); break;                 // 419
                    default: break;
                }
                Log.i(TAG, "toggle " + t + " -> " + on);
            } catch (Throwable e) {
                Log.w(TAG, "toggle " + t + ": " + e);
                Ui.toast(app, R.string.toast_failed);
            }
        });
        if (!queued) Ui.toast(this, R.string.toast_not_ready);
        refreshToggles();                               // FIFO: the read-back runs after the write
    }

    // ------------------------------------------------------------------ remote app keys
    private Kit.Row tempRow;

    /** IGmpf 438 getSystemTemperature() -> °C, as stock Device info; the row stays hidden without an answer. */
    private void refreshTemperature() {
        if (tempRow == null) return;
        Hal.query((g, g2) -> g.getSystemTemperature(), t -> {
            if (isFinishing() || isDestroyed() || tempRow == null) return;
            boolean ok = t != null && t > 0 && t < 150;
            tempRow.setShown(ok);
            if (ok) tempRow.value.setText(getString(R.string.p6_temperature_value, t));
        });
    }

    private void refreshSlots() {
        slot3Row.value.setText(slotText(3));
        slot4Row.value.setText(slotText(4));
        refreshLauncher();
    }

    // ------------------------------------------------------------------ home screen
    private void refreshLauncher() {
        if (launcherRow == null) return;
        boolean classic = org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC.equals(
                org.lumen.projector.home.LauncherSwitcher.currentMode(this));
        launcherRow.value.setText(classic ? R.string.launcher_classic : R.string.launcher_lumen);
    }

    private void refreshCecWake() {
        new Thread(() -> {
            final Boolean on = org.lumen.projector.cec.CecPolicy.wakeOn(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || cecWakeRow == null) return;
                cecWakeRow.setShown(on != null);
                if (on != null) cecWakeRow.value.setText(on ? R.string.panel_on : R.string.panel_off);
            });
        }, "p6-cec-read").start();
    }

    private void toggleCecWake() {
        cecWakeRow.value.setText("…");
        new Thread(() -> {
            Boolean on = org.lumen.projector.cec.CecPolicy.wakeOn(this);
            if (on != null && !org.lumen.projector.cec.CecPolicy.setWake(this, !on)) runOnUiThread(() -> Ui.toast(this, R.string.toast_failed));
            runOnUiThread(this::refreshCecWake);
        }, "p6-cec-write").start();
    }

    private void refreshBootSource() {
        if (bootSourceRow != null) bootSourceRow.value.setText(bootSourceItems()[org.lumen.projector.source.BootSource.get(this)]);
    }

    private CharSequence[] bootSourceItems() {
        return new CharSequence[] {getString(R.string.source_home), getString(R.string.panel_src_hdmi)};
    }

    private void pickBootSource() {
        int cur = org.lumen.projector.source.BootSource.get(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.p6_boot_source)
                .setSingleChoiceItems(bootSourceItems(), cur, (d, which) -> {
                    d.dismiss();
                    org.lumen.projector.source.BootSource.set(this, which);
                    refreshBootSource();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickLauncher() {
        final String[] modes = {org.lumen.projector.home.LauncherSwitcher.MODE_LUMEN,
                org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC};
        CharSequence[] items = {getString(R.string.launcher_lumen), getString(R.string.launcher_classic)};
        String cur = org.lumen.projector.home.LauncherSwitcher.currentMode(this);
        int checked = modes[1].equals(cur) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.launcher_row)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    if (modes[which].equals(cur)) return;
                    if (!KeyReceiver.isSetupComplete(this)) {
                        Ui.toast(this, R.string.toast_setup_running);
                        return;
                    }
                    launcherRow.value.setText("…");
                    org.lumen.projector.home.LauncherSwitcher.apply(this, modes[which], true, "projector settings",
                            (ok, holder, err) -> { if (!isFinishing() && !isDestroyed()) refreshLauncher(); });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String slotText(int slot) {
        String chosen = AppSlots.chosenPackage(this, slot);
        String eff = AppSlots.effectivePackage(this, slot);
        String label = AppSlots.labelOf(this, eff);
        return chosen == null ? getString(R.string.slot_default_fmt, label) : label;
    }

    private void pickApp(final int slot) {
        final List<AppSlots.AppEntry> apps = AppSlots.launchableApps(this);
        final CharSequence[] items = new CharSequence[apps.size() + 1];
        items[0] = getString(R.string.slot_default_item, AppSlots.labelOf(this, AppSlots.defaultPackage(this, slot)));
        for (int i = 0; i < apps.size(); i++) items[i + 1] = apps.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(slot == 3 ? R.string.key_slot3 : R.string.key_slot4)
                .setItems(items, (d, which) -> {
                    AppSlots.setPackage(this, slot, which == 0 ? null : apps.get(which - 1).pkg);
                    refreshSlots();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ diagnostics
    /**
     * One read of every diagnostic value on p6-hal, then the 404 feature list on p6-long (appended). Nothing
     * on a build without the Diagnostics section.
     */
    private void refreshDiagnostics() {
        if (diagBox == null) return;
        final int gen = ++diagGen;
        final String head = localLines();
        if (!Hal.isGateOpen()) {
            showDiag(gen, head + "\nHAL: start gate closed (nothing read)", false);
            return;
        }
        Hal.query(SettingsActivity::readDiagnostics, text -> {
            showDiag(gen, head + "\n" + (text != null ? text : "HAL: not connected (nothing read)"), false);
            if (text == null) return;
            boolean queued = Hal.runLong((g, g2) -> {
                String f;
                try {
                    f = Probe.featureSummary(g.getAllFeatureList());                  // 404
                } catch (Throwable t) {
                    f = "error: " + t;
                }
                final String line = "404 getAllFeatureList: " + f;
                Ui.main().post(() -> showDiag(gen, line, true));
            });
            if (!queued) showDiag(gen, "404 getAllFeatureList: not read (p6-long busy)", true);
        });
    }

    /** Lines without a HAL call (main thread). */
    private String localLines() {
        StringBuilder sb = new StringBuilder();
        line(sb, "ro.lumen.version", prop("ro.lumen.version"));
        line(sb, "ro.product.model", prop("ro.product.model"));
        String gm = prop("init.svc.gmpf_main");
        line(sb, "gmpf_main", "running".equals(gm) ? "running" : ("NOT running (" + gm + ")"));
        line(sb, "gmpfHw", prop("init.svc.gmpfHw"));
        line(sb, "Device.isPlay6 / hasBattery", Device.isPlay6() + " / " + Device.hasBattery());
        line(sb, "Start gate", Hal.isGateOpen() ? "open" : "closed");
        line(sb, "IGmpf", Hal.isReady() ? "connected" : "not connected");
        line(sb, "IGmpf2 guard", Hal.hasGmpf2() ? "open" : "closed");
        MotorController m = MotorController.peek();
        line(sb, "Focus motor", m == null ? "not used" : m.describe());
        line(sb, "Auto keystone", Hal.isKeystoneRunning() ? "running" : "idle");
        line(sb, "Battery", Battery.describe());
        line(sb, "Battery cards", BatteryNotices.describe());
        line(sb, "Vendor power listener", Hal.describePowerListener());
        line(sb, "Standby", org.lumen.projector.power.StandbyController.describe());
        line(sb, "HDMI source", org.lumen.projector.source.HdmiSource.describe());
        line(sb, "IGmpf hash (recorded)", Probe.IGMPF_RECORDED + " (live chain not read)");
        sb.append(getString(R.string.diag_app_version, versionName()));
        return sb.toString();
    }

    /** p6-hal: the read-only values of plan 1.1 and 3.4. */
    private static String readDiagnostics(Play6Gmpf g, Play6Gmpf2 g2) {
        StringBuilder sb = new StringBuilder();
        read(sb, "390 / 394 systemUIState / bootAnimState", () -> g.getSystemUiState() + " / " + g.getBootAnimState());
        read(sb, "588 autofocus after moving", g::getMoveAf);
        read(sb, "402 vendor HDMI auto switch", g::getHdmiAutoSwitch);
        read(sb, "438 system temperature", g::getSystemTemperature);
        read(sb, "240 battery level", g::getBatteryLevel);
        read(sb, "238 adapter powered", g::isAdapterPowered);
        read(sb, "239 battery full", g::isBatteryFull);
        read(sb, "327 power saving", g::getEcoState);
        if (g2.isIdentityOpen()) {
            Integer src = null;
            try {
                src = g2.getPowerSourceType();
                line(sb, "IGmpf2 52 power source", String.valueOf(src));
            } catch (Throwable t) {
                line(sb, "IGmpf2 52 power source", "error: " + t);
            }
            if (src != null && (src == 1 || src == 4)) read(sb, "IGmpf2 51 stand battery", g2::getStandBatteryLevel);
            else line(sb, "IGmpf2 51 stand battery", "not read (no stand)");
            read(sb, "IGmpf2 46 supply voltage (unit unverified)", g2::getPowerSupplyVoltage);
            read(sb, "IGmpf2 47 auto eco", g2::getAutoEcoMode);
            read(sb, "IGmpf2 212 ALLM status (HDMI)", g2::getAllmStatus);
        } else {
            line(sb, "IGmpf2 values", "not read (guard closed)");
        }
        try {
            String[] c = g2.liveHashChain();
            String e0 = c.length > 0 ? c[0] : "-";
            String v = Play6Gmpf2.recordedVendor(e0);
            line(sb, "IGmpf2 hash (live)", e0 + (v != null ? " = recorded " + v : " != recorded"));
        } catch (Throwable t) {
            line(sb, "IGmpf2 hash (live)", "error: " + t);
        }
        line(sb, "IGmpf2 hash (recorded)", Play6Gmpf2.RECORDED_HASH + " (v1.7.116), "
                + Play6Gmpf2.RECORDED_HASH_V1_1_67 + " (v1.1.67)");
        return sb.toString().trim();
    }

    private interface Read { Object get() throws Exception; }

    private static void read(StringBuilder sb, String k, Read r) {
        try {
            line(sb, k, String.valueOf(r.get()));
        } catch (Throwable t) {
            line(sb, k, "error: " + t);
        }
    }

    private static void line(StringBuilder sb, String k, String v) {
        sb.append(k).append(": ").append(v == null || v.isEmpty() ? "n/a" : v).append('\n');
    }

    private static String prop(String k) {
        try {
            return SystemProperties.get(k);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Main thread: replaces (or, with {@code append}, extends) the lines of refresh {@code gen}. */
    private void showDiag(int gen, String text, boolean append) {
        if (diagBox == null || isFinishing() || isDestroyed() || gen != diagGen) return;
        if (!append) diagBox.removeAllViews();
        for (String line : text.split("\n")) {
            TextView t = Kit.text(this, line, 10.5f, Kit.T2, Typeface.MONOSPACE);
            t.setFocusable(true);                  // D-pad can scroll through the lines
            t.setPadding(Kit.dp(this, 12), Kit.dp(this, 3), Kit.dp(this, 12), Kit.dp(this, 3));
            t.setOnFocusChangeListener((v, f) -> {
                v.setBackgroundColor(f ? Kit.S2 : 0x00000000);
                ((TextView) v).setTextColor(f ? Kit.T1 : Kit.T2);
            });
            diagBox.addView(t);
        }
    }

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Throwable t) {
            return "?";
        }
    }
}
