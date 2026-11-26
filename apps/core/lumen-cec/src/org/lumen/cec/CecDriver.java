// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.cec;

import android.content.Context;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * One projector's HDMI-CEC under the shared page (org.lumen.projector.cec.CecPage): the rows its stock has and how its
 * two power switches are read and written. The rest of the page is the same everywhere (the framework settings
 * through CecSettings, the HDMI app's own switches through its provider). The page calls every read and write on
 * {@link #worker()}, never on the main thread.
 */
public interface CecDriver {
    /** The rows this projector shows (its stock settings tree has them); the page keeps its own order. */
    Set<CecRow> rows();

    /** The thread of every read and write (binder, provider and vendor HAL calls), one task at a time, in order. */
    Executor worker();

    /** Once, when the page is built. */
    void install(Context app);

    /** HDMI-CEC on; null = unknown (no CEC service). */
    Boolean masterOn(Context c);

    boolean setMaster(Context c, boolean on);

    /** HDMI devices may turn the projector on; null = unknown. */
    Boolean wakeOn(Context c);

    boolean setWake(Context c, boolean on);

    /** Wake-ups by HDMI devices are paused for now (the page shows the note of {@link CecRow#NIGHT}). */
    boolean wakePaused();

    /** After every read of the page. */
    void afterRead(Context c);

    /** The other rows can be used only while HDMI-CEC is on (greyed otherwise); false = always usable (stock X50). */
    boolean rowsNeedMaster();
}
