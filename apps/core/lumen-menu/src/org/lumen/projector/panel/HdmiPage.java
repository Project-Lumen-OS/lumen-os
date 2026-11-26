// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.lumen.hdmiports.HdmiPort;
import org.lumen.hdmiports.HdmiPorts;
import org.lumen.projector.R;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The HDMI page of every projector (settings.hdmi, All settings, Input source; the HDMI-CEC page's HDMI options row
 * opens it too), in the menu's own rows: the two switches and the source at power-on of org.lumen.tvinput (the stock
 * signal page's signal_source_setting_auto and signal_boot_source_info), then every HDMI port with what is on it. The
 * options are the HDMI app's (lumen-hdmiports HdmiPorts.prefs), the port states the one PortState of that app, read
 * the same way Lumen Home's chips read them; OK on a port opens it. It replaces the HDMI app's own settings screen.
 */
final class HdmiPage {
    /** What the page needs from the menu. Main thread. */
    interface Host {
        void refreshPage(Page page);
    }

    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> new Thread(r, "lumen-hdmi-page"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context app;
    private final Host host;
    final Page page;
    private final ToggleRow autoSwitch, returnHome;
    private final ChoiceRow boot;
    private final HeaderRow portsHeader;
    private final List<View> portRows = new ArrayList<>();
    /** Ports offered by the power-on choice after home, in its order. */
    private final List<Integer> bootPorts = new ArrayList<>();
    private String portsSig = "";
    private HdmiPorts.Watch watch;

    HdmiPage(Context c, Host host) {
        app = c.getApplicationContext();
        this.host = host;
        page = new Page(s(R.string.lumen_m_hdmi_options));
        autoSwitch = page.add(new ToggleRow(app, s(R.string.lumen_m_hdmi_auto_switch),
                (row, on) -> write(row, () -> HdmiPorts.setPref(app, HdmiPorts.AUTO_SWITCH, on))));
        returnHome = page.add(new ToggleRow(app, s(R.string.lumen_m_hdmi_return_home),
                (row, on) -> write(row, () -> HdmiPorts.setPref(app, HdmiPorts.RETURN_HOME, on))));
        boot = page.add(new ChoiceRow(app, s(R.string.lumen_m_hdmi_boot_source), new CharSequence[]{
                s(R.string.lumen_home_screen)}, (row, i) -> {
                    int port = i <= 0 || i > bootPorts.size() ? HdmiPorts.BOOT_HOME : bootPorts.get(i - 1);
                    BG.execute(() -> { HdmiPorts.setBootSource(app, port); load(); });
                }));
        boot.setCommitDelay(MenuController.CHOICE_COMMIT_MS);
        page.add(new TextRow(app, s(R.string.lumen_m_hdmi_boot_note)));
        portsHeader = page.add(new HeaderRow(app, s(R.string.lumen_m_hdmi_inputs)));
        autoSwitch.setPending(true);
        returnHome.setPending(true);
        page.setOnShown(this::shown);
    }

    private String s(int res) {
        return app.getString(res);
    }

    /** Shown: read now, and again on every port change while the page is up (stopped by the next read off it). */
    private void shown() {
        if (watch == null) watch = HdmiPorts.watch(app, MAIN, () -> { if (page.rows().get(0).isAttachedToWindow()) reload(); else stopWatch(); });
        reload();
    }

    private void stopWatch() {
        if (watch != null) watch.close();
        watch = null;
    }

    private void reload() {
        BG.execute(this::load);
    }

    private void write(ToggleRow row, java.util.function.BooleanSupplier w) {
        row.setPending(true);
        BG.execute(() -> {
            w.getAsBoolean();
            load();
        });
    }

    /** Worker: the options and the ports, then one apply on the main thread. */
    private void load() {
        Bundle prefs = HdmiPorts.prefs(app);
        List<HdmiPort> ports = HdmiPorts.read(app);
        MAIN.post(() -> apply(prefs, ports == null ? new ArrayList<>() : ports));
    }

    private void apply(Bundle prefs, List<HdmiPort> ports) {
        autoSwitch.setPending(false).setChecked(prefs == null ? null : prefs.getBoolean(HdmiPorts.AUTO_SWITCH));
        returnHome.setPending(false).setChecked(prefs == null ? null : prefs.getBoolean(HdmiPorts.RETURN_HOME));
        bootPorts.clear();
        List<CharSequence> opts = new ArrayList<>();
        opts.add(s(R.string.lumen_home_screen));
        for (HdmiPort p : ports) {
            bootPorts.add(p.port);
            opts.add(app.getString(R.string.lumen_source_hdmi, p.port));
        }
        boot.setOptions(opts.toArray(new CharSequence[0]));
        int cur = prefs == null ? HdmiPorts.BOOT_HOME : prefs.getInt(HdmiPorts.BOOT_SOURCE, HdmiPorts.BOOT_HOME);
        boot.setSelected(Math.max(0, bootPorts.indexOf(cur) + 1));
        String sig = sig(ports);
        if (sig.equals(portsSig)) {
            for (int i = 0; i < portRows.size() && i < ports.size(); i++) {
                ((NavRow) portRows.get(i)).setValue(stateText(app, ports.get(i)));
            }
            return;
        }
        portsSig = sig;
        List<View> rows = page.rows();
        for (View r : portRows) rows.remove(r);
        portRows.clear();
        for (HdmiPort p : ports) {
            final int port = p.port;
            NavRow r = new NavRow(app, title(p), () -> org.lumen.source.SourceKey.openPort(app, port, "hdmi_page"));
            r.setValue(stateText(app, p));
            portRows.add(r);
        }
        if (ports.isEmpty()) portRows.add(new TextRow(app, s(R.string.lumen_m_hdmi_none)));
        rows.addAll(rows.indexOf(portsHeader) + 1, portRows);
        host.refreshPage(page);
    }

    /** The port's label, then the device on it when it has a name (the CEC name is a label, never the state). */
    static String title(HdmiPort p) {
        String label = p.label != null && !p.label.isEmpty() ? p.label : "HDMI " + p.port;
        return p.title == null ? label : label + "  " + p.title;
    }

    /** The one wording of a port state, the same on Lumen Home's chips (HdmiPort's states, lumen-common-strings). */
    static String stateText(Context c, HdmiPort p) {
        switch (p.state) {
            case HdmiPort.ON: return c.getString(R.string.lumen_connected);
            case HdmiPort.ASLEEP: return c.getString(R.string.lumen_asleep);
            case HdmiPort.NO_SIGNAL: return c.getString(R.string.lumen_no_signal);
            default: return c.getString(R.string.lumen_not_connected);
        }
    }

    private static String sig(List<HdmiPort> ports) {
        StringBuilder b = new StringBuilder();
        for (HdmiPort p : ports) b.append(p.port).append('|').append(title(p)).append(';');
        return b.toString();
    }
}
