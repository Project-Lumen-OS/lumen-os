// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * A rule the owner has not decided on yet (family data flag Family.INACTIVE_SOURCE_SLEEP, off on every family): the
 * watched device says {@code <Inactive Source>} and does not come back as the active source within
 * {@value #WAIT_MS} ms while the standby switch is on and no key was pressed: the projector sleeps. Plain Java over the
 * CEC history of dumpsys hdmi_control (CecLog), which SourceFollow reads every {@value #READ_MS} ms only while the
 * flag is on, a port is shown and the switch is on (no read at all with the flag off). Nothing is sent to any device.
 */
public final class InactiveSleep {
    public static final long WAIT_MS = 5_000;
    public static final long READ_MS = 2_500;

    /** Opcodes: Inactive Source, Active Source, Image View On, Text View On, User Control Pressed. */
    static final int INACTIVE = 0x9D, ACTIVE = 0x82, IMAGE_VIEW_ON = 0x04, TEXT_VIEW_ON = 0x0D, KEY = 0x44;

    private final CecLog log = new CecLog();
    /** The device that said {@code <Inactive Source>}, -1 = none pending. */
    private int target = -1;
    private long since;

    /** A new viewing (another port, shown again, the flag or the switch changed). */
    public void reset() {
        log.reset();
        target = -1;
    }

    /** A key of the projector's own (menu, global keys): the user is there, nothing pending sleeps. */
    public void key() {
        target = -1;
    }

    /**
     * One read at {@code now} (uptime ms). {@code dump}: the dumpsys hdmi_control text (null: the read failed);
     * {@code behind}: the logical addresses of the sources behind the shown port; {@code on}: the flag and the standby
     * switch; {@code awake}: the projector is awake. True once per {@code <Inactive Source>}: sleep now.
     */
    public boolean read(long now, String dump, int[] behind, boolean on, boolean awake) {
        if (!on || !awake) {                                    // a later read is a new baseline
            reset();
            return false;
        }
        for (CecLog.Entry e : log.fresh(dump)) {
            if (e == null || e.opcode < 0) continue;
            if (e.dir == 'S') {
                if (e.opcode == KEY) target = -1;               // a key forwarded to a device
                continue;
            }
            if (e.opcode == INACTIVE && has(behind, e.initiator)) {
                target = e.initiator;
                since = now;
            } else if (e.opcode == ACTIVE
                    || (e.initiator == target && (e.opcode == IMAGE_VIEW_ON || e.opcode == TEXT_VIEW_ON))) {
                target = -1;                                    // back, or another device is the source now
            }
        }
        if (target < 0 || now - since < WAIT_MS) return false;
        target = -1;
        return true;
    }

    private static boolean has(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }
}
