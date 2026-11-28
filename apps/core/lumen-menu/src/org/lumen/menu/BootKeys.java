// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The remote power key while the projector is still starting, one rule for every press machine (core power PowerKey and
 * Play6PowerKey, the X50 family's PowerPress through X50Power). Stock (G0148 services.jar PhoneWindowManager
 * interceptKeyBeforeQueueing): no key acts before the system is booted, and every key is dropped while the boot
 * animation runs ("bootanim is running, ignore key"), so a start never ends in the power menu, a sleep or a power off.
 * Lumen holds the key until Android reports the start complete ({@link #BOOT_COMPLETED} 1), since the press that opened
 * the power menu on a tester's HB came 0.9 s after the boot animation and 2 s before that (LR-P2ZK15, a start by his
 * HDMI device), and the menu's 15 s count then powered the projector off by itself. A press that went down during the
 * start does nothing, held or not; the next press acts. Plain Java: the callers read the two system properties.
 */
public final class BootKeys {
    /** Android's start complete ("1" once BOOT_COMPLETED is posted). */
    public static final String BOOT_COMPLETED = "sys.boot_completed";
    /** The init state of the boot animation service ("running" while it shows). */
    public static final String BOOTANIM = "init.svc.bootanim";

    private BootKeys() {}

    /** Null when a power press that goes down now may act, else why it does nothing. */
    public static String refuse(String bootCompleted, String bootanim) {
        if ("running".equals(bootanim)) return "boot animation running";
        if (!"1".equals(bootCompleted)) return "start not complete";
        return null;
    }
}
