// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemProperties;
import android.util.Log;

import org.lumen.menu.GeneralChoices;
import org.lumen.projector.battery.Battery;
import org.lumen.projector.battery.BatteryNotices;
import org.lumen.projector.diag.Probe;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.panel.SettingsRows;

import java.util.function.Consumer;

/**
 * The Play 6 part of the shared settings rows: IGmpf 420 / 419 through its HIDL facade (the row hides when 420 does not
 * answer), the statistics switch this app keeps (lumen-stats PrefsState: the Play 6 image has no updater), and the
 * diagnostics of test builds only (ro.lumen.version play6-t..., stock analysis 6.4): the start gate,
 * IGmpf / IGmpf2 and the guard, the hash chains, 588, 402, 438, the battery raw values, the vendor power listener,
 * standby, HDMI and the 404 feature list on p6-long. No interface resolution on the Play 6.
 */
public final class Play6SettingsDevice implements SettingsRows.Device {
    private static final String TAG = "P6Settings";
    private final Context app;

    public Play6SettingsDevice(Context c) { app = c.getApplicationContext(); }

    @Override public void music(Consumer<Boolean> cb) {
        Hal.query((g, g2) -> {
            try {
                return g.getPowerOnMusic();                                    // 420
            } catch (Throwable t) {
                Log.w(TAG, "get music: " + t);
                return null;
            }
        }, cb::accept);
    }

    @Override public void setMusic(boolean on, Consumer<Boolean> cb) {
        Hal.query((g, g2) -> {
            try {
                g.setPowerOnMusic(on);                                         // 419
                return g.getPowerOnMusic();
            } catch (Throwable t) {
                Log.w(TAG, "set music: " + t);
                return null;
            }
        }, cb::accept);
    }

    @Override public String[] bootTokens() { return GeneralChoices.BOOT_ONE_HDMI.clone(); }

    @Override public Boolean stats() {
        return org.lumen.stats.Ping.configured() ? org.lumen.stats.PrefsState.get(app).on() : null;
    }

    @Override public Boolean setStats(boolean on) {
        if (!org.lumen.stats.Ping.configured()) return null;
        org.lumen.stats.PrefsState stats = org.lumen.stats.PrefsState.get(app);
        stats.setOn(on);
        return stats.on();
    }

    @Override public boolean diagnostics() { return Device.isTestBuild(); }

    @Override public boolean uiResolution() { return false; }

    @Override public void openUiResolution() {}

    /** One read of every diagnostic value on p6-hal, then the 404 feature list on p6-long (a second, longer text). */
    @Override public void readDiagnostics(Consumer<String> cb) {
        final String head = localLines();
        if (!Hal.isGateOpen()) {
            cb.accept(head + "\nHAL: start gate closed (nothing read)");
            return;
        }
        Hal.query(Play6SettingsDevice::readOnHal, text -> {
            final String first = head + "\n" + (text != null ? text : "HAL: not connected (nothing read)");
            cb.accept(first);
            if (text == null) return;
            boolean queued = Hal.runLong((g, g2) -> {
                String f;
                try {
                    f = Probe.featureSummary(g.getAllFeatureList());                  // 404
                } catch (Throwable t) {
                    f = "error: " + t;
                }
                final String line = "404 getAllFeatureList: " + f;
                Ui.main().post(() -> cb.accept(first + "\n" + line));
            });
            if (!queued) cb.accept(first + "\n404 getAllFeatureList: not read (p6-long busy)");
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
        line(sb, "Standby", org.lumen.projector.power.LampStandby.describe());
        line(sb, "HDMI source", org.lumen.projector.source.HdmiSource.describe());
        line(sb, "IGmpf hash (recorded)", Probe.IGMPF_RECORDED + " (live chain not read)");
        sb.append(app.getString(R.string.diag_app_version, versionName()));
        return sb.toString();
    }

    /** p6-hal: the read-only values of plan 1.1 and 3.4. */
    private static String readOnHal(Play6Gmpf g, Play6Gmpf2 g2) {
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

    private String versionName() {
        try {
            PackageInfo pi = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            return pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Throwable t) {
            return "?";
        }
    }
}
