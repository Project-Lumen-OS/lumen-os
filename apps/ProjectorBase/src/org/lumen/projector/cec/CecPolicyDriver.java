package org.lumen.projector.cec;

import android.content.Context;

import org.lumen.cec.CecDriver;
import org.lumen.cec.CecRow;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The Z9X and Z9X HB driver of the shared HDMI-CEC page: every row but the two X50 notes, the two power switches as the
 * framework settings they are (CecSettings), and this app's power policy behind them (CecPolicy: its worker, the storm
 * pause of wake-ups, the boot default of the PM51 wake source cec0 after every read).
 */
public final class CecPolicyDriver implements CecDriver {
    private static final Set<CecRow> ROWS = EnumSet.of(CecRow.MASTER, CecRow.WAKE, CecRow.NIGHT, CecRow.SWITCH,
            CecRow.SEND_STANDBY, CecRow.SOUNDBAR, CecRow.SOUNDBAR_ON, CecRow.REMOTE, CecRow.LEAVE_NOTICE, CecRow.DEVICES,
            CecRow.HDMI_OPTIONS);

    @Override public Set<CecRow> rows() { return ROWS; }

    @Override public Executor worker() { return CecPolicy.worker()::post; }

    @Override public void install(Context app) { CecPolicy.install(app); }

    @Override public Boolean masterOn(Context c) { return CecSettings.masterOn(c); }

    @Override public boolean setMaster(Context c, boolean on) { return CecSettings.setMaster(c, on); }

    @Override public Boolean wakeOn(Context c) { return CecSettings.wakeOn(c); }

    @Override public boolean setWake(Context c, boolean on) { return CecSettings.setWake(c, on); }

    @Override public boolean wakePaused() { return CecPolicy.stormPaused(); }

    @Override public void afterRead(Context c) { CecSettings.syncWakeProp(c); }

    @Override public boolean rowsNeedMaster() { return true; }
}
