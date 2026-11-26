// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package parity;

import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvInputInfo;
import android.view.View;

import org.lumen.projector.cec.CecPage;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The CEC page of one projector app, driven through every row in fixed scenarios: what it shows (rows, labels in
 * each locale, values, enabled, visible) and what it does (framework, provider, vendor HAL, props, prefs, toasts,
 * screens opened, on which thread). The transcript is pinned per device in golden/; a move of the page must leave it
 * byte-equal. Usage: CecParity STRINGS_DIR (one LOCALE.tsv per locale: name TAB text).
 */
public final class CecParity {
    private static final StringBuilder OUT = new StringBuilder();
    private static Page shown;

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        for (Field f : Class.forName("org.lumen.projector.R$string").getFields()) World.names.put(f.getInt(null), f.getName());
        String[] locales = dir.list((d, n) -> n.endsWith(".tsv"));
        Arrays.sort(locales);
        for (String file : locales) {
            String loc = file.substring(0, file.length() - 4);
            World.texts = load(new File(dir, "default.tsv"));
            if (!loc.equals("default")) World.texts.putAll(load(new File(dir, file)));
            boolean full = loc.equals("default") || loc.equals("ru");
            out("######## locale " + loc);
            for (String sc : full ? SCENARIOS : new String[]{"all_on"}) scenario(sc, full);
        }
        System.out.print(OUT);
    }

    static final String[] SCENARIOS = {"all_on", "cec_off", "no_service", "throws", "no_tvinput", "paused", "soundbar_off",
            "setup_running", "writes_fail"};

    private static java.util.Map<String, String> load(File f) throws IOException {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            int t = line.indexOf('\t');
            if (t > 0) m.put(line.substring(0, t), line.substring(t + 1).replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"));
        }
        return m;
    }

    private static void setup(String sc) {
        World.reset();
        World.providers.add("org.lumen.tvinput.hdmistate");
        World.tvPrefs.put("cec_one_touch_play", true);
        World.tvPrefs.put("cec_control", true);
        World.tvPrefs.put("cec_internal_on_exit", false);
        World.deviceName = "Living room";
        World.hal.put("getCecWakeUpState", Boolean.TRUE);
        World.devices.add(new HdmiDeviceInfo(11, 4, 1, HdmiDeviceInfo.DEVICE_PLAYBACK, HdmiControlManager.POWER_STATUS_ON, "PS5", true));
        World.devices.add(new HdmiDeviceInfo(12, 5, 3, HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM, HdmiControlManager.POWER_STATUS_STANDBY, "", true));
        World.devices.add(new HdmiDeviceInfo(13, 3, 2, HdmiDeviceInfo.DEVICE_TUNER, HdmiControlManager.POWER_STATUS_UNKNOWN, " ", true));
        World.devices.add(new HdmiDeviceInfo(14, 8, 2, HdmiDeviceInfo.DEVICE_PLAYBACK, HdmiControlManager.POWER_STATUS_TRANSIENT_TO_ON, "Box", true));
        World.devices.add(new HdmiDeviceInfo(15, 0, 0, HdmiDeviceInfo.DEVICE_TV, HdmiControlManager.POWER_STATUS_ON, "TV", true));
        World.devices.add(new HdmiDeviceInfo(16, 15, 1, HdmiDeviceInfo.DEVICE_PLAYBACK, HdmiControlManager.POWER_STATUS_ON, "Free", true));
        World.devices.add(new HdmiDeviceInfo(17, 4, 0, HdmiDeviceInfo.DEVICE_PLAYBACK, HdmiControlManager.POWER_STATUS_ON, "No port", true));
        World.devices.add(new HdmiDeviceInfo(18, 4, 3, HdmiDeviceInfo.DEVICE_PLAYBACK, HdmiControlManager.POWER_STATUS_ON, "MHL", false));
        World.inputs.add(new TvInputInfo("org.lumen.tvinput/.HdmiInputService/HDMI100004", "org.lumen.tvinput/.HdmiInputService/HW1",
                World.devices.get(0), "org.lumen.tvinput"));
        World.inputs.add(new TvInputInfo("other/.In/HDMI2", "other/.In/HW2", World.devices.get(3), "other"));
        World.inputs.add(new TvInputInfo("org.lumen.tvinput/.HdmiInputService/HW1", null, null, "org.lumen.tvinput"));
        switch (sc) {
            case "cec_off": World.hdmi.cec = 0; break;
            case "no_service": World.hdmi = null; break;
            case "throws": World.hdmiThrows = true; World.deviceNameThrows = true; World.hal.put("getCecWakeUpState", null); break;
            case "no_tvinput": World.providers.clear(); break;
            case "paused": World.paused = true; break;
            case "soundbar_off": World.hdmi.sysAudio = 0; World.hdmi.wake = 0; World.hal.put("getCecWakeUpState", Boolean.FALSE); break;
            case "setup_running": World.setupDone = false; break;
            case "writes_fail":
                World.hdmiSetThrows.addAll(Arrays.asList("setHdmiCecEnabled", "setTvWakeOnOneTouchPlay", "setTvSendStandbyOnSleep",
                        "setSystemAudioControl", "setHdmiCecVolumeControlEnabled"));
                World.tvSetFails = true;
                World.hal.put("setCecWakeUp", null);
                break;
            default: break;
        }
    }

    private static final CecPage.Host HOST = new CecPage.Host() {
        @Override public void refreshPage(Page page) {
            World.event("host.refreshPage " + (page == shown ? "shown page" : "other page"));
            if (page == shown && page.onShown() != null) page.onShown().run();
        }
        @Override public void closeThen(Runnable r) {
            World.event("host.closeThen");
            World.post("main", r, 0);
        }
        @Override public void openHdmiOptions() { World.event("host.openHdmiOptions"); }
        @Override public boolean setupDone(String what) {
            World.event("host.setupDone " + what + " -> " + World.setupDone);
            return World.setupDone;
        }
    };

    private static void scenario(String sc, boolean full) {
        setup(sc);
        out("==== scenario " + sc);
        Context app = new Context();
        Page page = Entry.build(app, HOST);
        step("build");
        shown = page;
        if (page.onShown() != null) page.onShown().run();
        step("shown");
        dump(page);
        if (!full) return;
        for (View v : new ArrayList<>(page.rows())) {
            if (v instanceof NavRow && v.getVisibility() == View.VISIBLE && ((NavRow) v).onClick() != null) {
                ((NavRow) v).onClick().run();
                step("click " + label(v));
            }
        }
        List<View> rows = new ArrayList<>(page.rows());
        for (View v : rows) {
            if (!page.rows().contains(v) || v.getVisibility() != View.VISIBLE) continue;
            if (v instanceof ToggleRow) {
                ((ToggleRow) v).flip();
                step("flip " + label(v));
                dump(page);
            } else if (v instanceof ChoiceRow) {
                ChoiceRow c = (ChoiceRow) v;
                for (int k : new int[]{0, c.options().length - 1, 2}) {
                    c.choose(k);
                    step("choose " + label(v) + " #" + k);
                }
                dump(page);
            }
        }
        if (page.onShown() != null) page.onShown().run();
        step("shown again");
        dump(page);
    }

    private static void step(String what) {
        World.event("~ " + what);
        World.drain();
        for (String e : World.EVENTS) out("  " + e);
        World.EVENTS.clear();
    }

    private static String label(View v) {
        if (v instanceof Row) return "'" + ((Row) v).getTitle() + "'";
        if (v instanceof TextRow) return "'" + ((TextRow) v).text() + "'";
        if (v instanceof HeaderRow) return "'" + ((HeaderRow) v).text() + "'";
        return v.getClass().getSimpleName();
    }

    private static void dump(Page p) {
        out("  page '" + p.getTitle() + "'");
        for (View v : p.rows()) {
            StringBuilder b = new StringBuilder("    ");
            b.append(v.getVisibility() == View.VISIBLE ? "" : "(gone) ");
            b.append(v.getClass().getSimpleName()).append(' ').append(label(v));
            if (v instanceof Row && !((Row) v).isRowEnabled()) b.append(" [grey]");
            if (v instanceof ToggleRow) b.append(" = ").append(((ToggleRow) v).isChecked());
            if (v instanceof ChoiceRow) {
                ChoiceRow c = (ChoiceRow) v;
                b.append(" = #").append(c.getSelected()).append(" of ").append(Arrays.toString(c.options()))
                        .append(" commit ").append(c.commitDelay()).append(" ms");
            }
            if (v instanceof NavRow) {
                NavRow n = (NavRow) v;
                b.append(" value '").append(n.getValue()).append("'").append(n.chevron() ? " >" : "")
                        .append(n.onClick() == null ? " (no action)" : "");
            }
            out(b.toString());
        }
    }

    private static void out(String s) { OUT.append(s).append('\n'); }
}
