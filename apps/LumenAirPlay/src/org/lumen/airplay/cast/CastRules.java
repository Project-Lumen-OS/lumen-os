/*
 * Copyright (C) 2026 Lumen OS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.lumen.airplay.cast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The decisions of the casting screen, plain Java (host tested): the state of each receiver card from what the
 * receivers report, which cards show and which receiver is in use. No model checks: the model data is CastModels.
 */
public final class CastRules {
    private CastRules() {}

    /** AirPlay server state names (org.lumen.airplay ServerState). */
    static final String STOPPED = "STOPPED", STARTING = "STARTING", RUNNING = "RUNNING", ERROR = "ERROR";

    /**
     * The AirPlay card: off when switched off, starting until the server runs, connected while a sender mirrors, plays
     * music or a video, not visible when the network hides it, ready otherwise.
     */
    public static CardState airplay(boolean enabled, String server, boolean session, boolean notVisible) {
        if (!enabled) return CardState.OFF;
        if (ERROR.equals(server)) return CardState.ERROR;
        if (!RUNNING.equals(server)) return CardState.STARTING;
        if (session) return CardState.CONNECTED;
        return notVisible ? CardState.NOT_VISIBLE : CardState.READY;
    }

    /**
     * The screen mirroring card from the Miracast sink's control provider (call "state": enabled, code =
     * ReceiverStatus name, mirroring). Absent: no sink in this image or no Wi-Fi Direct on this unit (its provider is
     * off then, LumenMiracast ControlGate) or the sink reports UNSUPPORTED. A sink that did not answer is an error.
     */
    public static CardState miracast(boolean present, boolean answered, boolean enabled, String code, boolean mirroring) {
        if (!present) return CardState.ABSENT;
        if (!answered) return CardState.ERROR;
        if (mirroring || "IN_USE".equals(code)) return CardState.CONNECTED;
        if (!enabled || "OFF".equals(code)) return CardState.OFF;
        if ("UNSUPPORTED".equals(code)) return CardState.ABSENT;
        if ("NO_WIFI".equals(code)) return CardState.NO_WIFI;
        if ("STARTING".equals(code)) return CardState.STARTING;
        if ("FAULT".equals(code)) return CardState.ERROR;
        return "READY".equals(code) ? CardState.READY : CardState.ERROR;
    }

    /**
     * The text of the screen mirroring card: the sink's cause (detail) while the card is starting or not working and
     * the sink named one, otherwise how to connect.
     */
    public static String mirroringBody(CardState st, String detail, String how) {
        boolean waiting = st == CardState.STARTING || st == CardState.ERROR;
        return waiting && detail != null && !detail.isEmpty() ? detail : how;
    }

    /**
     * The DLNA card from Lumen Play, the renderer every image carries: absent without the app, off while the app is
     * disabled or its renderer does not answer (DLNA switched off in Lumen Play), needs a network (Wi-Fi or cable)
     * before phones can see it, ready when the renderer answers on this unit (DlnaProbe).
     */
    public static CardState dlna(boolean installed, boolean enabled, boolean network, boolean answered) {
        if (!installed) return CardState.ABSENT;
        if (!enabled) return CardState.OFF;
        if (!network) return CardState.NO_WIFI;
        return answered ? CardState.READY : CardState.OFF;
    }

    /** The cards in screen order: AirPlay always, screen mirroring and DLNA unless absent. */
    public static List<Receiver> cards(CardState miracast, CardState dlna) {
        List<Receiver> out = new ArrayList<>();
        out.add(Receiver.AIRPLAY);
        if (miracast != CardState.ABSENT) out.add(Receiver.MIRACAST);
        if (dlna != CardState.ABSENT) out.add(Receiver.DLNA);
        return Collections.unmodifiableList(out);
    }

    /** The receiver in use for the headline, AirPlay first; null when none is. */
    public static Receiver inUse(CardState airplay, CardState miracast) {
        if (airplay == CardState.CONNECTED) return Receiver.AIRPLAY;
        return miracast == CardState.CONNECTED ? Receiver.MIRACAST : null;
    }

    /** Both receivers are switched off (or the sink is absent): the headline asks to turn one on. */
    public static boolean allOff(CardState airplay, CardState miracast) {
        return airplay == CardState.OFF && (miracast == CardState.OFF || miracast == CardState.ABSENT);
    }

    /** A chip that reads as good news (ready or in use): the screen paints it in the ok colour. */
    public static boolean good(CardState s) {
        return s == CardState.READY || s == CardState.CONNECTED;
    }

    /** The screen's switch rows follow the receiver: the screen mirroring rows only while its card shows. */
    public static boolean mirroringRows(CardState miracast) {
        return miracast != CardState.ABSENT;
    }
}
