// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.lumen.menu.GeneralChoices;
import org.lumen.projector.display.UiResolution;
import org.lumen.projector.panel.SettingsRows;
import org.lumen.projector.sys.UpdaterStats;

import java.util.function.Consumer;

/**
 * The Z9X and (through the recipe) Z9X HB part of the shared settings rows: IGmpf 420 / 419 through the app's gmpf
 * client, the statistics switch the updater owns (UpdaterStats), HalController's diagnostics, the interface
 * resolution chooser. The HB gates the resolution row by its feature table (HbMenuGates).
 */
public final class SettingsDevice implements SettingsRows.Device {
    private final Context app;

    public SettingsDevice(Context c) { app = c.getApplicationContext(); }

    @Override public void music(Consumer<Boolean> cb) {
        Hal.query((g, g2) -> {
            try {
                return g.getPowerOnMusic();
            } catch (Throwable t) {
                return null;
            }
        }, cb::accept);
    }

    @Override public void setMusic(boolean on, Consumer<Boolean> cb) {
        Hal.query((g, g2) -> {
            try {
                g.setPowerOnMusic(on);
                return g.getPowerOnMusic();
            } catch (Throwable t) {
                return null;
            }
        }, cb::accept);
    }

    @Override public String[] bootTokens() { return GeneralChoices.BOOT_TWO_HDMI.clone(); }

    @Override public Boolean stats() { return UpdaterStats.get(app); }

    @Override public Boolean setStats(boolean on) { return UpdaterStats.set(app, on); }

    @Override public boolean diagnostics() { return true; }

    @Override public void readDiagnostics(Consumer<String> cb) {
        HalController.get(app).readDiagnostics(text -> cb.accept(text + "\n"
                + app.getString(R.string.diag_app_version, versionName(app)) + "\n" + UiResolution.diagLine(app)));
    }

    @Override public boolean uiResolution() { return UiResolution.enabled(); }

    @Override public void openUiResolution() { UiResolution.openChooser(app); }

    static String versionName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Throwable t) {
            return "?";
        }
    }
}
