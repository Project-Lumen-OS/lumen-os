/*
 * Copyright (C) 2026 Lumen OS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.lumen.airplay.cast;

/**
 * Lumen Play's DLNA renderer as this unit sees it: while DLNA is on, its HTTP server answers the device description on
 * a fixed port on every address (kino-player dlna/Renderer PORT). Plain Java (host tested): the request and the reading
 * of the answer; the socket is the screen's. If another app holds the port, Lumen Play takes a free one and the card
 * reads it as off.
 */
public final class DlnaProbe {
    private DlnaProbe() {}

    /** kino-player dlna/Renderer PORT. */
    public static final int PORT = 49595;
    public static final String REQUEST = "GET /description.xml HTTP/1.0\r\nHost: 127.0.0.1:" + PORT + "\r\n\r\n";
    /** UPnP device type of a media renderer (any version). */
    static final String RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:";

    /** A renderer answered: status 200 and a media renderer description. */
    public static boolean renderer(String answer) {
        if (answer == null || !answer.startsWith("HTTP/1.")) return false;
        int sp = answer.indexOf(' ');
        return sp > 0 && answer.startsWith("200 ", sp + 1) && answer.contains(RENDERER);
    }

    /** The name phones list for the renderer (its friendlyName), or null when the answer has none. */
    public static String name(String answer) {
        if (!renderer(answer)) return null;
        int a = answer.indexOf("<friendlyName>"), b = answer.indexOf("</friendlyName>");
        if (a < 0 || b < a) return null;
        String n = answer.substring(a + "<friendlyName>".length(), b).trim();
        n = n.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
        return n.isEmpty() ? null : n;
    }
}
