// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast.core;

import java.util.ArrayDeque;

/**
 * Does the system's Wi-Fi Direct link still answer, and what does the receiver do about it. Host tested.
 *
 * The framework answers ERROR for every Wi-Fi Direct call once it lost its handle to the supplicant's P2P interface.
 * That happens on the MediaTek TV vendors when the framework calls a supplicant method the vendor lacks (the
 * vendor's supplicant says AIDL V3 but was built from a snapshot without configureExtListenWithParams and
 * connectWithParams: startListening and accepting a connection both lose the handle). Only a new set-up of the
 * interface brings it back, and Android 14 does that when the last client closes its channel and the next call opens
 * it again. So:
 * - BUSY (Wi-Fi is connecting and blocks discovery) is a wait, never a fault.
 * - {@link #ERRORS_TO_RESTART} ERROR answers in a row: reopen the channel ({@link Action#RESTART}), at most
 *   {@link #MAX_RESTARTS} times in {@link #RESTART_WINDOW_MS}; then the fault is {@link Fault#NOT_ANSWERING}.
 * - The link died right after a connection was accepted ({@link #lostAfterAccept}): the system cannot finish a
 *   connection on this unit ({@link Fault#CONNECT_BROKEN}); reopen at once, outside the restart budget, so phones see
 *   the projector again. The fault stays for this system build (the caller keeps it across restarts).
 */
public final class P2pHealth {
    /** WifiP2pManager.ERROR and BUSY. */
    public static final int REASON_ERROR = 0;
    public static final int REASON_BUSY = 2;
    public static final int ERRORS_TO_RESTART = 2;
    public static final int MAX_RESTARTS = 3;
    public static final long RESTART_WINDOW_MS = 10 * 60_000L;

    /** What the status line says beyond the plain state. */
    public enum Fault { NONE, WAITING_WIFI, RESTARTING, NOT_ANSWERING, CONNECT_BROKEN }

    public enum Action { NONE, RESTART }

    private int errors;
    private boolean busy, restarting, gaveUp, connectBroken;
    private final ArrayDeque<Long> restarts = new ArrayDeque<>();

    /** The cause to show: a lasting fault first, then a passing one. */
    public Fault fault() {
        if (gaveUp) return Fault.NOT_ANSWERING;
        if (connectBroken) return Fault.CONNECT_BROKEN;
        if (restarting) return Fault.RESTARTING;
        if (busy) return Fault.WAITING_WIFI;
        return Fault.NONE;
    }

    /** The fault of a name (Fault.name()), NONE for null or an unknown name. */
    public static Fault fault(String name) {
        if (name != null) for (Fault f : Fault.values()) if (f.name().equals(name)) return f;
        return Fault.NONE;
    }

    /** A fault the user must know about (the card shows it as not working). */
    public static boolean lasting(Fault f) {
        return f == Fault.NOT_ANSWERING || f == Fault.CONNECT_BROKEN;
    }

    /** A Wi-Fi Direct call succeeded: the link answers. */
    public void ok() {
        errors = 0;
        busy = false;
        restarting = false;
        gaveUp = false;
    }

    /** A Wi-Fi Direct call failed with this WifiP2pManager reason at {@code now} (elapsed ms). */
    public Action failed(int reason, long now) {
        if (reason == REASON_BUSY) {
            busy = true;
            return Action.NONE;
        }
        if (reason != REASON_ERROR) return Action.NONE;
        if (++errors < ERRORS_TO_RESTART) return Action.NONE;
        while (!restarts.isEmpty() && now - restarts.peekFirst() >= RESTART_WINDOW_MS) restarts.pollFirst();
        if (restarts.size() >= MAX_RESTARTS) {
            gaveUp = true;
            restarting = false;
            return Action.NONE;
        }
        restarts.addLast(now);
        errors = 0;
        restarting = true;
        gaveUp = false;
        return Action.RESTART;
    }

    /** The link died right after a connection was accepted: the system cannot finish connections on this unit. */
    public Action lostAfterAccept() {
        connectBroken = true;
        errors = 0;
        restarting = true;
        return Action.RESTART;
    }

    /** Known from an earlier start of the same system build. */
    public void knownConnectBroken() {
        connectBroken = true;
    }

    public boolean connectBroken() {
        return connectBroken;
    }

    /** Wi-Fi went off: nothing is waiting for it to connect any more. */
    public void wifiOff() {
        busy = false;
        errors = 0;
    }
}
