// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import java.util.Collection;

/**
 * AutoPick: the output of the sound output choice "Auto", the stock rule on every Lumen projector. Plain Java, host
 * tested (test/run.sh). Paths are MediaPath values.
 *
 * Stock (libxgimiaudiopolicy.so of the Z9X HB G0148, Egn_setDeviceConnectionState / Egn_getDeviceForMedia /
 * Egn_setForceUse; Auto = vendor.xgimi.system.audiodevice.switchmode 0):
 * <ol>
 *   <li>A device connected while Auto is on becomes lastAccessDevice and plays (A2DP, wired headphones, USB, HDMI ARC,
 *       the speaker; SPDIF never), and it ends what the last manual choice left (egnForceUse FOR_MEDIA = 0).</li>
 *   <li>lastAccessDevice disconnected: cleared. A manual choice (setForceUse FOR_MEDIA): cleared too.</li>
 *   <li>Without lastAccessDevice the output of the last manual choice keeps playing while it is connected
 *       (egnForceUse is not reset when Auto is turned on), and is forgotten once it is not.</li>
 *   <li>Else the first connected one of A2DP, wired, USB, HDMI ARC, the speaker.</li>
 * </ol>
 * So with Bluetooth headphones on, a soundbar whose ARC comes up later takes the sound, and the headphones take it back
 * when they connect again; AOSP 14 instead always prefers a removable output and adds ARC to every route.
 */
public final class AutoPick {
    /** Egn_getDeviceForMedia without lastAccessDevice: A2DP, wired, USB, ARC, then the speaker. */
    static final int[] FALLBACK = {MediaPath.BT, MediaPath.WIRED, MediaPath.USB, MediaPath.ARC, MediaPath.SPEAKER};

    private int lastAccess = MediaPath.NONE;
    private int manual = MediaPath.NONE;

    /** A device of this path was connected; auto: the stored choice is Auto (stock updates only then). */
    public synchronized void added(int path, boolean auto) {
        if (!auto || !candidate(path)) return;
        lastAccess = path;
        manual = MediaPath.NONE;
    }

    /** A device of this path was disconnected. */
    public synchronized void removed(int path) {
        if (path == lastAccess) lastAccess = MediaPath.NONE;
    }

    /** The user chose an output (a path); Auto itself changes nothing (stock: only the switch mode). */
    public synchronized void chose(int path) {
        if (!candidate(path)) return;
        lastAccess = MediaPath.NONE;
        manual = path;
    }

    /** The Auto output among the connected paths, NONE when none is a candidate. */
    public synchronized int pick(Collection<Integer> connected) {
        if (connected == null) return MediaPath.NONE;
        if (lastAccess != MediaPath.NONE && connected.contains(lastAccess)) return lastAccess;
        if (manual != MediaPath.NONE) {
            if (connected.contains(manual)) return manual;
            manual = MediaPath.NONE;
        }
        for (int p : FALLBACK) if (connected.contains(p)) return p;
        return MediaPath.NONE;
    }

    private static boolean candidate(int path) {
        for (int p : FALLBACK) if (p == path) return true;
        return false;
    }
}
